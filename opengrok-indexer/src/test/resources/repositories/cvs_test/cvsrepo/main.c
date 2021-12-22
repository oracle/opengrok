#include "header.h"

/* Added comment  */

int main(int argc, char **argv) {

   printf("Program %s executed with the following arguments:\n", argv[0]);
   for (int i = 1; i < argc; ++i) {
      printf("[%s] ", argv[i]);
   }
   printf("\n");

   return EXIT_SUCCESS;
}
