
#define MAXERROR '1'

typedef struct mytype {
	int	status;
	char	*message;
} mytype_t;

char
farguments(const char *serverType,
		char **Root,
		mytype_t **errorp,
		int anon_fallback,
		void (*f)(int),
		mytype_t & param)
{
	char errmsg=MAXERROR;
	return errmsg;
}

