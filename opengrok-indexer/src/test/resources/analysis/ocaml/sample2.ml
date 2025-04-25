(* Test comments and extension nodes *)

(* "*)" *)

let _c = 'c' and
    _d = '\78' and
    _e = '\o003' and
    _f = '\xAf'

(* {|*)|} *)

let str = {| (* *) |}

(* '"' *)

let _ = [%string {| (* *) |}]

(* f' '"' *)

let f = {%string | (* *) |}]
