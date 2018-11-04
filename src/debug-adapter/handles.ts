// Copyright 2018 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * A map-like class that stores data in an integer-indexed structure where the indexes are
 * autogenerated.
 */
export class Handles<T> {
  /** The auto-incremented index of the next handle to be created. */
  private nextHandle = 1;

  /** The values for which handles have been created, stored with the handle number as the key. */
  private values = new Map<number, T>();

  /** Creates and returns a new handle for the given value. */
  public create(value: T): number {
    const handle = this.nextHandle++;
    this.values.set(handle, value);
    return handle;
  }

  /** Retrieves the value with the given handle. */
  public get(handle: number): T {
    return this.values.get(handle);
  }

  /** Clears all the handles and stored values. */
  public clear() {
    this.nextHandle = 1;
    this.values = new Map<number, T>();
  }
}
