// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;

public class CheckCast extends Format21c<DexType> {

  public static final int OPCODE = 0x1f;
  public static final String NAME = "CheckCast";
  public static final String SMALI_NAME = "check-cast";

  CheckCast(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getTypeMap());
  }

  public CheckCast(int valueRegister, DexType type) {
    super(valueRegister, type);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public void collectIndexedItems(
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriter) {
    getType().collectIndexedItems(indexedItems);
  }

  @Override
  public CheckCast asCheckCast() {
    return this;
  }

  @Override
  public boolean isCheckCast() {
    return true;
  }

  @Override
  public void registerUse(UseRegistry registry) {
    registry.registerCheckCast(getType());
  }

  public DexType getType() {
    return BBBB;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addCheckCast(AA, getType());
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
