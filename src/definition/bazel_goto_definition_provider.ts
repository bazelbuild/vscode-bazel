// Copyright 2021 The Bazel Authors. All rights reserved.
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

import {
  CancellationToken,
  Definition,
  DefinitionLink,
  DefinitionProvider,
  Location,
  Position,
  TextDocument,
  Uri,
} from "vscode";
import { BazelQuery, BazelWorkspaceInfo, QueryLocation } from "../bazel";
import { getDefaultBazelExecutablePath } from "../extension/configuration";
import { Utils } from "vscode-uri";

// adapted from https://github.com/bazelbuild/buildtools/blob/d6daef01a1a2f41a4143a314bf1996bf351caa30/build/rewrite.go#L183
// LABEL_REGEX matches label strings, e.g. @r//x/y/z:abc
// where $2 is @r//x/y/z, $3 is @r//, $4 is r, $5 is x/y/z, $6 is z, $7 is abc.
const LABEL_REGEX = /"((?:@\w+)?\/\/|(?:.+\/)?[^:]*(?::[^:]+)?)"/;

export class BazelGotoDefinitionProvider implements DefinitionProvider {
  public async provideDefinition(
    document: TextDocument,
    position: Position,
    _token: CancellationToken,
  ): Promise<Definition | DefinitionLink[]> {
    const workspaceInfo = BazelWorkspaceInfo.fromDocument(document);
    if (workspaceInfo === undefined) {
      // Not in a Bazel Workspace.
      return null;
    }

    const range = document.getWordRangeAtPosition(position, LABEL_REGEX);
    const targetText = document.getText(range);
    const match = LABEL_REGEX.exec(targetText);

    const targetName = match[1];
    if (targetName.startsWith("//visibility")) {
      return null;
    }

    const queryResult = await new BazelQuery(
      getDefaultBazelExecutablePath(),
      Utils.dirname(document.uri).fsPath,
      `kind(rule, "${match[1]}")`,
      [],
    ).queryTargets();

    if (!queryResult.target.length) {
      return null;
    }
    const location = new QueryLocation(queryResult.target[0].rule.location);
    return new Location(Uri.file(location.path), location.range);
  }
}
