#include "header.h"

int main(int argc, char **argv) {

   (void)printf("Program %s executed with the following arguments:\n", argv[0]);
   for (int i = 1; i < argc; ++i) {
      (void)printf("[%s] ", argv[i]);
   }
   (void)printf("\n");

   return EXIT_SUCCESS;
}
