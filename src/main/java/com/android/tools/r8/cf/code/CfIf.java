// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import java.util.function.BiFunction;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfIf extends CfConditionalJumpInstruction {

  public CfIf(If.Type kind, ValueType type, CfLabel target) {
    super(kind, type, target);
  }

  @Override
  public int getCompareToId() {
    return getOpcode();
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    CfIf otherIf = (CfIf) other;
    assert kind == otherIf.kind;
    assert type == otherIf.type;
    return helper.compareLabels(target, otherIf.target, visitor);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    // The compare-id distinguishes types and we have no label identity to use.
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseNormalTargets(
      BiFunction<? super CfInstruction, ? super CT, TraversalContinuation<BT, CT>> fn,
      CfInstruction fallthroughInstruction,
      CT initialValue) {
    return fn.apply(target, initialValue)
        .ifContinueThen(
            continuation -> fn.apply(fallthroughInstruction, continuation.getValueOrDefault(null)));
  }

  public int getOpcode() {
    switch (kind) {
      case EQ:
        return type.isObject() ? Opcodes.IFNULL : Opcodes.IFEQ;
      case GE:
        return Opcodes.IFGE;
      case GT:
        return Opcodes.IFGT;
      case LE:
        return Opcodes.IFLE;
      case LT:
        return Opcodes.IFLT;
      case NE:
        return type.isObject() ? Opcodes.IFNONNULL : Opcodes.IFNE;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    visitor.visitJumpInsn(getOpcode(), target.getLabel());
  }
  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int value = state.pop().register;
    int trueTargetOffset = code.getLabelOffset(target);
    int falseTargetOffset = code.getCurrentInstructionIndex() + 1;
    builder.addIfZero(kind, type, value, trueTargetOffset, falseTargetOffset);
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., value →
    // ...
    return frame.popInitialized(appView, config, type);
  }
}
