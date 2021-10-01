/* Test for scopes, weird eols, last line is not empty(will it detect last definition tag scope?) */

extern "C" {
  #include "stdlib.h"
}

unsigned int makeW(unsigned int w) { return w; }
unsigned int makeW(unsigned char h, unsigned char l) { return (h << 8) | l; }