// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;

abstract class SgetOrSput extends Format21c<DexField> {

  SgetOrSput(int high, BytecodeStream stream, DexField[] map) {
    super(high, stream, map);
  }

  protected SgetOrSput(int AA, DexField BBBB) {
    super(AA, BBBB);
  }

  @Override
  public final void collectIndexedItems(
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriter) {
    getField().collectIndexedItems(indexedItems);
  }

  @Override
  public final DexField getField() {
    return BBBB;
  }
}
