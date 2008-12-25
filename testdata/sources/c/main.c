#include "header.h"

int main(int argc, char **argv) {

   double ii;
   ii = 123.4 + 0x432 + 4ul;
   char c = 'x';
   /*
   Multi line comment, with embedded strange characters: < > &,
   email address: testuser@example.com and even an URL:
   http://www.example.com/index.html and a file name and a path:
   <example.cpp> and </usr/local/example.h>.
   Ending with an email address: username@example.com
   */
   printf("Program %s executed with the following \"arguments\":\n", argv[0]);
   for (int i = 1; i < argc; ++i) {
      printf("[%s] ", argv[i]);
   }
   printf("\n");

   /* Short comment */
   return EXIT_SUCCESS;
}
