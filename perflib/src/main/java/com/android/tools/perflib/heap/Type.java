/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.perflib.heap;

import com.google.common.collect.Maps;

import java.util.Map;

public enum Type {
    OBJECT(2, 0),
    BOOLEAN(4, 1),
    CHAR(5, 2),
    FLOAT(6, 4),
    DOUBLE(7, 8),
    BYTE(8, 1),
    SHORT(9, 2),
    INT(10, 4),
    LONG(11, 8);

    private static int sIdSize = 4;

    private static Map<Integer, Type> sTypeMap = Maps.newHashMap();

    private int mId;

    private int mSize;

    static {
        for (Type type : Type.values()) {
            sTypeMap.put(type.mId, type);
        }
    }

    Type(int type, int size) {
        mId = type;
        mSize = size;
    }

    public static final void setIdSize(int size) {
        sIdSize = size;
    }

    public static Type getType(int id) {
        return sTypeMap.get(id);
    }

    public int getSize() {
        return this == OBJECT ? sIdSize : mSize;
    }
}

