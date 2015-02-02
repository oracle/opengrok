// compile me with gcc
/* this is sample comment } */
#include <stdio.h>
#include <string.h>

#define TEST(x) (x)

int foo(int a, int b) {
    /* blah blah
        }
    */

    int c;
    const char *msg = "this is } sample { string";
    if (a < b) {
        return strlen(msg);
    } else {
        // }}}}} something to return
        c = TEST(a) + TEST(b);
    }
    return c;
}

int bar(int x /* } */)
{
    // another function
    int d;
    int f;
    printf(TEST("test { message|$#@$!!#"));
    d = foo(2, 4);
    f = foo(x, d);

    /* return
        some
         rubish
    */
    return d+f;
}

// main function
int main(int argc, char *argv[]) {
    int res;
    printf("this is just a {sample}}");

    res = bar(20);
    printf("result = {%d}\n", res);

    return 0; }

