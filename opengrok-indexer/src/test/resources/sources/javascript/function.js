/*
 * Sample js program
 */

var date = new Date(96, 11, 25); 
//reference
var ref = date;

ref.setDate(21);

date.getDate(); //21

// The same is true when objects and arrays are passed to functions.
// The following function adds a value to each element of an array.
// A reference to the array is passed to the function, not a copy of the array.
// Therefore, the function can change the contents of the array through
// the reference, and those changes will be visible when the function returns.
function main(totals, x)
{
    totals[0] = totals[0] + x;
    totals[1] = totals[1] + x;
    totals[2] = totals[2] + x;
}

var numberliteral = 0x4f;

(date == ref)           // Evaluates to true

var ndate= new Date(96, 11, 25);  
var sameobjectasndate = new Date(96, 11, 25);

(ndate != sameobjectasndate)    // true !

