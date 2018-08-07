#!/bin/ksh

Usage() {
	printf 'Usage: %s [-p] [-n] [-h] [image_dir]

 Options:
   -p .. Combine straight to PNG. Otherwise first pics are combined into a
         GIF and than converted to PNG.
   -n .. No action, i.e. show what would be done without actually doing it.
   -h .. Print this help and exit.

 Combines several images into a sprite named combined.png and prints out
 the required CSS infos. E.g.:

 %s web/offwhite/img
' "$(basename $0)" "$(basename $0)"
}

GIF="gif"
ECHO=""
while getopts "h(help)n(dry)p(png)" option ; do
	case "$option" in
		h) Usage ; exit 0 ;;
		p) GIF="png" ;;
		n) ECHO="echo" ;;
	esac
done
IDX=$(($OPTIND-1))
shift $IDX

if [ -n "$1" ]; then
	cd "$1"
	[ $? -eq 0 ] || exit 1
fi
F_NAV="h.gif l.gif w.gif"
F_LST="d.gif p.gif"
F_MISC="Logo.png servedby.png rss.png q.gif" # q.gif must be the last

FILES="$F_MISC $F_LST $F_NAV"
FILES="$F_NAV $F_LST $F_MISC"

OUT="combined"

# concat to GIF (results in smaller pics than to PNG directly)
$ECHO montage -background Transparent -tile x1 -mode Concatenate $FILES ${OUT}.$GIF
if [ "$GIF" = "gif" ]; then
# convert to PNG (transparent pics are rendered badly by FF on none-transparent
# BGs)
$ECHO convert ${OUT}.$GIF ${OUT}.png
fi

integer X=0
[ -n "$ECHO" ] && $ECHO "identify -format '%f %w %h\\\n' $FILES $OUT.png"
identify -format "%f %w %h\n" $FILES $OUT.png | while read F W H T; do
	[ -z "$F" ] && continue
	printf "%s  background-position: -%dpx %dpx; width: %dpx; height: %dpx;\n" \
		"$F" $X 0 $W $H
	X=$((X+$W))
done
