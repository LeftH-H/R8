// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ir.optimize.nonnull.IntrinsicsDeputy;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamAfterInvokeDirect;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamAfterInvokeInterface;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamAfterInvokeInterfaceMain;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamAfterInvokeStatic;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamAfterInvokeVirtual;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamAfterInvokeVirtualMain;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamInterface;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamInterfaceImpl;
import com.android.tools.r8.ir.optimize.nonnull.NotPinnedClass;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonNullParamTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NonNullParamTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void disableDevirtualization(InternalOptions options) {
    options.enableDevirtualization = false;
  }

  private CodeInspector buildAndRun(
      Class<?> mainClass,
      Collection<Class<?>> classes,
      ThrowableConsumer<R8FullTestBuilder> configuration)
      throws Exception {
    String javaOutput = runOnJava(mainClass);

    return testForR8(parameters.getBackend())
        .addProgramClasses(classes)
        .addKeepMainRule(mainClass)
        .addKeepRules(ImmutableList.of("-keepattributes InnerClasses,Signature,EnclosingMethod"))
        // All tests are checking if invocations to certain null-check utils are gone.
        .noMinification()
        .addOptionsModification(
            options -> {
              // Need to increase a little bit to inline System.out.println
              options.inliningInstructionLimit = 4;
            })
        .apply(configuration)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(javaOutput)
        .inspector();
  }

  @Test
  public void testIntrinsics() throws Exception {
    Class<?> mainClass = IntrinsicsDeputy.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            ImmutableList.of(NeverInline.class, mainClass),
            R8TestBuilder::enableInliningAnnotations);

    ClassSubject mainSubject = inspector.clazz(mainClass);
    assertThat(mainSubject, isPresent());

    MethodSubject selfCheck = mainSubject.uniqueMethodWithName("selfCheck");
    assertThat(selfCheck, isPresent());
    assertEquals(1, countCallToParamNullCheck(selfCheck));
    assertEquals(1, countPrintCall(selfCheck));
    assertEquals(0, countThrow(selfCheck));

    MethodSubject checkNull = mainSubject.uniqueMethodWithName("checkNull");
    assertThat(checkNull, isPresent());
    assertEquals(1, countCallToParamNullCheck(checkNull));
    assertEquals(1, countPrintCall(checkNull));
    assertEquals(0, countThrow(checkNull));

    MethodSubject paramCheck = mainSubject.uniqueMethodWithName("nonNullAfterParamCheck");
    assertThat(paramCheck, isPresent());
    assertEquals(1, countPrintCall(paramCheck));
    assertEquals(0, countThrow(paramCheck));

    paramCheck = mainSubject.uniqueMethodWithName("nonNullAfterParamCheckDifferently");
    assertThat(paramCheck, isPresent());
    assertEquals(1, countPrintCall(paramCheck));
    assertEquals(0, countThrow(paramCheck));
  }

  @Test
  public void testNonNullParamAfterInvokeStatic() throws Exception {
    Class<?> mainClass = NonNullParamAfterInvokeStatic.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            ImmutableList.of(
                NeverInline.class, IntrinsicsDeputy.class, NotPinnedClass.class, mainClass),
            R8TestBuilder::enableInliningAnnotations);

    ClassSubject mainSubject = inspector.clazz(mainClass);
    assertThat(mainSubject, isPresent());

    MethodSubject checkViaCall = mainSubject.uniqueMethodWithName("checkViaCall");
    assertThat(checkViaCall, isPresent());
    assertEquals(0, countActCall(checkViaCall));
    assertEquals(2, countPrintCall(checkViaCall));

    MethodSubject checkViaIntrinsic = mainSubject.uniqueMethodWithName("checkViaIntrinsic");
    assertThat(checkViaIntrinsic, isPresent());
    assertEquals(0, countCallToParamNullCheck(checkViaIntrinsic));
    assertEquals(1, countPrintCall(checkViaIntrinsic));

    MethodSubject checkAtOneLevelHigher = mainSubject.uniqueMethodWithName("checkAtOneLevelHigher");
    assertThat(checkAtOneLevelHigher, isPresent());
    assertEquals(1, countPrintCall(checkAtOneLevelHigher));
    assertEquals(0, countThrow(checkAtOneLevelHigher));
  }

  @Test
  public void testNonNullParamAfterInvokeDirect() throws Exception {
    Class<?> mainClass = NonNullParamAfterInvokeDirect.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            ImmutableList.of(
                NeverInline.class, IntrinsicsDeputy.class, NotPinnedClass.class, mainClass),
            R8TestBuilder::enableInliningAnnotations);

    ClassSubject mainSubject = inspector.clazz(mainClass);
    assertThat(mainSubject, isPresent());

    MethodSubject checkViaCall = mainSubject.uniqueMethodWithName("checkViaCall");
    assertThat(checkViaCall, isPresent());
    assertEquals(0, countActCall(checkViaCall));
    assertEquals(2, countPrintCall(checkViaCall));

    MethodSubject checkViaIntrinsic = mainSubject.uniqueMethodWithName("checkViaIntrinsic");
    assertThat(checkViaIntrinsic, isPresent());
    assertEquals(0, countCallToParamNullCheck(checkViaIntrinsic));
    assertEquals(1, countPrintCall(checkViaIntrinsic));

    MethodSubject checkAtOneLevelHigher = mainSubject.uniqueMethodWithName("checkAtOneLevelHigher");
    assertThat(checkAtOneLevelHigher, isPresent());
    assertEquals(1, countPrintCall(checkAtOneLevelHigher));
    assertEquals(0, countThrow(checkAtOneLevelHigher));
  }

  @Test
  public void testNonNullParamAfterInvokeVirtual() throws Exception {
    Class<?> mainClass = NonNullParamAfterInvokeVirtualMain.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            ImmutableList.of(
                NeverInline.class,
                IntrinsicsDeputy.class,
                NonNullParamAfterInvokeVirtual.class,
                NotPinnedClass.class,
                mainClass),
            builder -> builder.enableClassInliningAnnotations().enableInliningAnnotations());

    ClassSubject mainSubject = inspector.clazz(NonNullParamAfterInvokeVirtual.class);
    assertThat(mainSubject, isPresent());

    MethodSubject checkViaCall = mainSubject.uniqueMethodWithName("checkViaCall");
    assertThat(checkViaCall, isPresent());
    assertEquals(0, countActCall(checkViaCall));
    assertEquals(2, countPrintCall(checkViaCall));

    MethodSubject checkViaIntrinsic = mainSubject.uniqueMethodWithName("checkViaIntrinsic");
    assertThat(checkViaIntrinsic, isPresent());
    assertEquals(0, countCallToParamNullCheck(checkViaIntrinsic));
    assertEquals(1, countPrintCall(checkViaIntrinsic));

    MethodSubject checkAtOneLevelHigher = mainSubject.uniqueMethodWithName("checkAtOneLevelHigher");
    assertThat(checkAtOneLevelHigher, isPresent());
    assertEquals(1, countPrintCall(checkAtOneLevelHigher));
    assertEquals(0, countThrow(checkAtOneLevelHigher));
  }

  @Test
  public void testNonNullParamAfterInvokeInterface() throws Exception {
    Class<?> mainClass = NonNullParamAfterInvokeInterfaceMain.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            ImmutableList.of(
                NeverInline.class,
                IntrinsicsDeputy.class,
                NonNullParamInterface.class,
                NonNullParamInterfaceImpl.class,
                NonNullParamAfterInvokeInterface.class,
                NotPinnedClass.class,
                mainClass),
            builder ->
                builder
                    .addOptionsModification(this::disableDevirtualization)
                    .enableInliningAnnotations()
                    .enableClassInliningAnnotations()
                    .enableMergeAnnotations());

    ClassSubject mainSubject = inspector.clazz(NonNullParamAfterInvokeInterface.class);
    assertThat(mainSubject, isPresent());

    MethodSubject checkViaCall = mainSubject.uniqueMethodWithName("checkViaCall");
    assertThat(checkViaCall, isPresent());
    assertEquals(0, countActCall(checkViaCall));
    // The DEX backend reuses the System.out.println invoke.
    assertEquals(parameters.isCfRuntime() ? 2 : 1, countPrintCall(checkViaCall));
  }

  private long countCallToParamNullCheck(MethodSubject method) {
    return countCall(method, IntrinsicsDeputy.class.getSimpleName(), "checkParameterIsNotNull");
  }

  private long countPrintCall(MethodSubject method) {
    return countCall(method, "PrintStream", "print");
  }

  private long countActCall(MethodSubject method) {
    return countCall(method, NotPinnedClass.class.getSimpleName(), "act");
  }

  private long countThrow(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(InstructionSubject::isThrow)).count();
  }
}
