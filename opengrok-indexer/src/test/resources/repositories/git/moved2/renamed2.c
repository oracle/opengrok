#include <stdio.h>
#include <stdlib.h>

int foo ( const char * path )
{
	return path && *path == 'A';
}

int main ( int argc, const char * argv[] )
{
	int i;
	for ( i = 1; i < argc; i ++ )
	{
		printf ( "%s called with %d\n", argv [ 0 ], argv [ i ] );
	}

	printf ( "Hello, world!\n" );

	if ( foo ( argv [ 0 ] ) )
	{
		printf ( "Correct call\n" );
	}

	return 0;
}
