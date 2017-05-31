{*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *}

// Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.

unit Sample;
uses
  User;

interface
  type
    TSample = class
    private
      FId : integer;
      FDescription : String;
      FUserId: integer; 
      function GetId : integer;
      procedure SetId(const aValue : integer);
    public
      class function GetClassName: string;
      function GetIdAndDescriptionString: string; virtual;
      function GetUser: TUser;
    published
      property Id: integer read GetId write SetId;
      property Description: string read FDescription write FDescription;
    end;

implementation
uses
  Logging;

function TSample.GetId: integer;
begin
  Result := FId;
end;

procedure TSample.SetId(const aValue: integer);
begin
  FId := aValue;
end;

class function TSample.GetClassName: string;
begin
  Result := 'TSample';
end;

function TSample.GetUser: TUser;
begin
  Result := TUser.Create(FUserId);
end;

end.
