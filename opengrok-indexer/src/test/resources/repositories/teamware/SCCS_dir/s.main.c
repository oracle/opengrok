h45802
s 00003/00003/00009
d D 1.2 08/08/12 22:11:54 trond 2 1
c Fixed lint warnings
e
s 00012/00000/00000
d D 1.1 08/08/12 22:09:23 trond 1 0
c date and time created 08/08/12 22:09:23 by trond
e
u
U
f e 0
t
T
I 1
#include "header.h"

int main(int argc, char **argv) {

D 2
   printf("Program %s executed with the following arguments:\n", argv[0]);
E 2
I 2
   (void)printf("Program %s executed with the following arguments:\n", argv[0]);
E 2
   for (int i = 1; i < argc; ++i) {
D 2
      printf("[%s] ", argv[i]);
E 2
I 2
      (void)printf("[%s] ", argv[i]);
E 2
   }
D 2
   printf("\n");
E 2
I 2
   (void)printf("\n");
E 2

   return EXIT_SUCCESS;
}
E 1
