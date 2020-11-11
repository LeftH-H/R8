// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.BaseCompilerCommandParser;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.tracereferences.TraceReferencesFormattingConsumer.OutputFormat;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

class TraceReferencesCommandParser {

  private static final Set<String> OPTIONS_WITH_PARAMETER =
      ImmutableSet.of("--lib", "--target", "--source", "--output");

  static final String USAGE_MESSAGE =
      String.join(
          "\n",
          Iterables.concat(
              Arrays.asList(
                  "Usage: tracereferences <command> [<options>] [@<argfile>]",
                  " Where <command> is one of:",
                  "  --check                 # Run emitting only diagnostics messages.",
                  "  --print-usage           # Traced references will be output in the print-usage",
                  "                          # format.",
                  "  --keep-rules [<keep-rules-options>]",
                  "                          # Traced references will be output in the keep-rules",
                  "                          # format.",
                  " and each <argfile> is a file containing additional options (one per line)",
                  " and options are:",
                  "  --lib <file|jdk-home>   # Add <file|jdk-home> runtime library.",
                  "  --source <file>         # Add <file> as a source for tracing references.",
                  "  [--target <file>]       # Add <file> as a target for tracing references. When",
                  "                          # target is not specified all references from source",
                  "                          # outside of library are treated as a missing",
                  "                          # references.",
                  "  --output <file>         # Output result in <outfile>. If not passed the",
                  "                          # result will go to standard out.",
                  "                          # result will go to standard out."),
              BaseCompilerCommandParser.MAP_DIAGNOSTICS_USAGE_MESSAGE,
              Arrays.asList(
                  "  --version               # Print the version of tracereferences.",
                  "  --help                  # Print this message.",
                  " and <keep-rule-options> are:",
                  "  --allowobfuscation      # Output keep rules with the allowobfuscation",
                  "                          # modifier (defaults to rules without the"
                      + " modifier)")));
  /**
   * Parse the tracereferences command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return tracereferences command builder with state set up according to parsed command line.
   */
  static TraceReferencesCommand.Builder parse(String[] args, Origin origin) {
    return new TraceReferencesCommandParser().parse(args, origin, TraceReferencesCommand.builder());
  }

  /**
   * Parse the tracereferences command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return tracereferences command builder with state set up according to parsed command line.
   */
  static TraceReferencesCommand.Builder parse(
      String[] args, Origin origin, DiagnosticsHandler handler) {
    return new TraceReferencesCommandParser()
        .parse(args, origin, TraceReferencesCommand.builder(handler));
  }

  private enum Command {
    CHECK,
    PRINTUSAGE,
    KEEP_RULES;

    private OutputFormat toOutputFormat(boolean allowobfuscation) {
      switch (this) {
        case PRINTUSAGE:
          return OutputFormat.PRINTUSAGE;
        case KEEP_RULES:
          return allowobfuscation
              ? OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION
              : OutputFormat.KEEP_RULES;
        default:
          throw new Unreachable();
      }
    }
  }

  private void checkCommandNotSet(
      Command command, TraceReferencesCommand.Builder builder, Origin origin) {
    if (command != null) {
      builder.error(new StringDiagnostic("Multiple commands specified", origin));
    }
  }

  private TraceReferencesCommand.Builder parse(
      String[] args, Origin origin, TraceReferencesCommand.Builder builder) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    Path output = null;
    Command command = null;
    boolean allowObfuscation = false;
    if (expandedArgs.length == 0) {
      builder.error(new StringDiagnostic("Missing command"));
      return builder;
    }
    // Parse options.
    for (int i = 0; i < expandedArgs.length; i++) {
      String arg = expandedArgs[i].trim();
      String nextArg = null;
      if (OPTIONS_WITH_PARAMETER.contains(arg)) {
        if (++i < expandedArgs.length) {
          nextArg = expandedArgs[i];
        } else {
          builder.error(
              new StringDiagnostic("Missing parameter for " + expandedArgs[i - 1] + ".", origin));
          break;
        }
      }
      if (arg.length() == 0) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
        return builder;
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
        return builder;
      } else if (arg.equals("--check")) {
        checkCommandNotSet(command, builder, origin);
        command = Command.CHECK;
      } else if (arg.equals("--print-usage")) {
        checkCommandNotSet(command, builder, origin);
        command = Command.PRINTUSAGE;
      } else if (arg.equals("--keep-rules")) {
        checkCommandNotSet(command, builder, origin);
        command = Command.KEEP_RULES;
      } else if (arg.equals("--allowobfuscation")) {
        allowObfuscation = true;
      } else if (arg.equals("--lib")) {
        addLibraryArgument(builder, origin, nextArg);
      } else if (arg.equals("--target")) {
        builder.addTargetFiles(Paths.get(nextArg));
      } else if (arg.equals("--source")) {
        builder.addSourceFiles(Paths.get(nextArg));
      } else if (arg.equals("--output")) {
        if (output != null) {
          builder.error(new StringDiagnostic("Option '--output' passed multiple times.", origin));
        } else {
          output = Paths.get(nextArg);
        }
      } else if (arg.startsWith("@")) {
        builder.error(new StringDiagnostic("Recursive @argfiles are not supported: ", origin));
      } else {
        int argsConsumed =
            BaseCompilerCommandParser.tryParseMapDiagnostics(
                builder::error, builder.getReporter(), arg, expandedArgs, i, origin);
        if (argsConsumed >= 0) {
          i += argsConsumed;
          continue;
        }
        builder.error(new StringDiagnostic("Unsupported option '" + arg + "'", origin));
      }
    }

    if (command == null) {
      builder.error(
          new StringDiagnostic(
              "Missing command, specify one of 'check', '--print-usage' or '--keep-rules'",
              origin));
      return builder;
    }

    if (command == Command.CHECK && output != null) {
      builder.error(
          new StringDiagnostic(
              "Using '--output' requires command '--print-usage' or '--keep-rules'", origin));
      return builder;
    }

    if (command != Command.KEEP_RULES && allowObfuscation) {
      builder.error(
          new StringDiagnostic(
              "Using '--allowobfuscation' requires command '--keep-rules'", origin));
      return builder;
    }

    final Path finalOutput = output;
    builder.setConsumer(
        command == Command.CHECK
            ? TraceReferencesConsumer.emptyConsumer()
            : new TraceReferencesFormattingConsumer(command.toOutputFormat(allowObfuscation)) {
              @Override
              public void finished() {
                PrintStream out = System.out;
                if (finalOutput != null) {
                  try {
                    out = new PrintStream(Files.newOutputStream(finalOutput));
                  } catch (IOException e) {
                    builder.error(new ExceptionDiagnostic(e));
                  }
                }
                out.print(get());
              }
            });
    return builder;
  }

  /**
   * This method must match the lookup in {@link
   * com.android.tools.r8.JdkClassFileProvider#fromJdkHome}.
   */
  private static boolean isJdkHome(Path home) {
    Path jrtFsJar = home.resolve("lib").resolve("jrt-fs.jar");
    if (Files.exists(jrtFsJar)) {
      return true;
    }
    // JDK has rt.jar in jre/lib/rt.jar.
    Path rtJar = home.resolve("jre").resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    // JRE has rt.jar in lib/rt.jar.
    rtJar = home.resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    return false;
  }

  static void addLibraryArgument(
      TraceReferencesCommand.Builder builder, Origin origin, String arg) {
    Path path = Paths.get(arg);
    if (isJdkHome(path)) {
      try {
        builder.addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(path));
      } catch (IOException e) {
        builder.error(new ExceptionDiagnostic(e, origin));
      }
    } else {
      builder.addLibraryFiles(path);
    }
  }
}
