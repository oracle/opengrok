(* The sample file. *)
print_string "Hello World!\n";;

let again = print_string and 
    string = {bar|Another string|bar} in
    (again [@tailcall]) string;; 
    (* Note, identifier 'string' is deliberately ignored 
                                                by tokenizer. *)

type 'a tau = Tau of 'a | Phi of 'a list | Omicron;;
(* Btw, do you know that
   'a is read as α
   'b is read as ß
   'c is γ - γάμμα ! *)

let weLovePolymorphicVariants = [`Right ; `OrNot ; `OrUnsure];;

let weLoveVariablesWithQuotes' = function None -> failwith "???"
                                        | Some reason -> 
                                                let _is_needed_for = 8n and
                                                    result = reason in
                                                failwith result;;
(* Note: 'result' is not ignored, like 'string' *)
let _sum_some_numbers = Int64.to_int 10_8_8L + 
    Nativeint.to_int 0xDEADF00Dn + Int32.to_int 0o76l + 0b101 +
                     Int32.to_int 0b111001l in
();;

let _float_around = 1.8E+23 +. 1_2_3_4.8_8E-2 in
();;
