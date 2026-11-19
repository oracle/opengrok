identification division.
program-id. sample-free.
*> Demonstrates free-format COBOL parsing.
data division.
working-storage section.
01 ws-customer.
   05 ws-name      pic x(30).
   05 ws-age       pic 9(03).
   05 ws-balance   pic 9(7)v99.
   05 ws-empno     pic 9(05).
procedure division.
main-para.
    move "JANE DOE" to ws-name.
    move 31         to ws-age.
    compute ws-balance = 2500.75 + 100.
    move "She said ""hi""" to ws-name.
    move 'JANE''S NAME'    to ws-name.
    *> Embedded SQL block (exercises SQL_BLOCK state).
    exec sql
       select empno into :ws-empno from employees where dept = 'IT'
    end-exec.
    display ws-name.
    stop run.