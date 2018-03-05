# Copyright (c) 2015, 2016, 2017 dbohdan
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

# Sqawk, an SQL Awk.
# Copyright (C) 2015, 2016, 2017 dbohdan
# License: MIT

namespace eval ::sqawk {}

# Performs SQL queries on files and channels.
::snit::type ::sqawk::sqawk {
    # Internal object state.
    variable tables {}
    variable defaultTableNames [split abcdefghijklmnopqrstuvwxyz ""]
    variable formatToParser
    variable formatToSerializer

    # Options.
    option -database
    option -ofs
    option -ors

    option -destroytables -default true
    option -outputformat -default awk
    option -parsers -default {} -configuremethod Set-and-update-format-list
    option -serializers -default {} -configuremethod Set-and-update-format-list

    # Methods.
    constructor {} {
        # Register parsers and serializers.
        $self configure -parsers [namespace children ::sqawk::parsers]
        $self configure -serializers [namespace children ::sqawk::serializers]
    }

    destructor {
        if {[$self cget -destroytables]} {
            dict for {_ tableObj} $tables {
                $tableObj destroy
            }
        }
    }

    # Update the related format dictionary when the parser or the serializer
    # list option is set.
    method Set-and-update-format-list {option value} {
        set optToDict {
            -parsers formatToParser
            -serializers formatToSerializer
        }
        set possibleOpts [dict keys $optToDict]
        if {$option ni $possibleOpts} {
            error "Set-and-update-format-list can't set the option \"$option\""
        }
        set options($option) $value

        set dictName [dict get $optToDict $option]
        set $dictName {}
        # For each parser/serializer...
        foreach ns $value {
            foreach format [set ${ns}::formats] {
                dict set $dictName $format $ns
            }
        }
    }

    # Parse $data from $format into a list of rows.
    method Parse {format data fileOptions} {
        set error [catch {
            set ns [dict get $formatToParser $format]
        }]
        if {$error} {
            error "unknown input format: \"$format\""
        }
        set parseOptions [set ${ns}::options]
        return [${ns}::parse $data \
                [::sqawk::override-keys $parseOptions $fileOptions]]
    }

    # Serialize a list of rows into text in the format $format.
    method Serialize {format data sqawkOptions} {
        # Parse $format.
        set splitFormat [split $format ,]
        set formatName [lindex $splitFormat 0]
        set formatOptions {}
        foreach option [lrange $splitFormat 1 end] {
            lassign [split $option =] key value
            lappend formatOptions $key $value
        }
        set error [catch {
            set ns [dict get $formatToSerializer $formatName]
        }]
        if {$error} {
            error "unknown output format: \"$formatName\""
        }

        # Get the dict containing the options the serializer accepts with their
        # default values.
        set so [set ${ns}::options]
        # Set the two main options for the "awk" serializer. "awk" is a special
        # case: its options are set based on separate command line arguments
        # whose values are passed to us in $sqawkOptions.
        if {$formatName eq {awk}} {
            if {[dict exists $formatOptions ofs]} {
                error {to set the field separator for the "awk" output format\
                        please use the command line option "-OFS" instead of\
                        the format option "ofs"}
            }
            if {[dict exists $formatOptions ors]} {
                error {to set the record separator for the "awk" output format\
                        please use the command line option "-OFS" instead of\
                        the format option "ofs"}
            }
            dict set so ofs [dict get $sqawkOptions -ofs]
            dict set so ors [dict get $sqawkOptions -ors]
        }
        # Check if all the serializer options we have been given in $format are
        # valid. Replace the default values with the actual values.
        foreach {key value} $formatOptions {
            if {[dict exists $so $key]} {
                dict set so $key $value
            } else {
                error "unknown option for output format\
                        \"$formatName\":\ \"$key\""
            }
        }
        return [${ns}::serialize $data $so]
    }

    # Read data from a file or a channel into a new database table. The filename
    # or channel to read from and the options for how to read and store the data
    # are in all set in the dictionary $fileOptions.
    method read-file fileOptions {
        # Set the default table name ("a", "b", "c", ..., "z").
        set defaultTableName [lindex $defaultTableNames [dict size $tables]]
        # Set the default column name prefix equal to the table name.
        ::sqawk::dict-ensure-default fileOptions table $defaultTableName
        ::sqawk::dict-ensure-default fileOptions F0 1
        ::sqawk::dict-ensure-default fileOptions csvquote \"
        ::sqawk::dict-ensure-default fileOptions csvsep ,
        ::sqawk::dict-ensure-default fileOptions format awk
        ::sqawk::dict-ensure-default fileOptions merge {}
        ::sqawk::dict-ensure-default fileOptions prefix \
                [dict get $fileOptions table]

        array set metadata $fileOptions

        # Read the data.
        if {[info exists metadata(channel)]} {
            set ch $metadata(channel)
        } elseif {$metadata(filename) eq "-"} {
            set ch stdin
        } else {
            set ch [open $metadata(filename)]
        }
        set raw [read $ch]
        close $ch

        set rows [$self Parse $metadata(format) $raw $fileOptions]
        unset raw

        # Create and configure a new table object.
        set newTable [::sqawk::table create %AUTO%]
        $newTable configure \
                -database [$self cget -database] \
                -dbtable $metadata(table) \
                -columnprefix $metadata(prefix) \
                -f0 $metadata(F0) \
                -maxnf $metadata(NF) \
                -modenf $metadata(MNF)
        # Configure datatypes.
        if {[info exists metadata(datatypes)]} {
            $newTable configure -datatypes [split $metadata(datatypes) ,]
        }
        # Configure column names.
        set header {}
        if {[info exists metadata(header)] && $metadata(header)} {
            # Remove the header from $rows.
            set rows [lassign $rows headerF0]
            # Strip the first field (a0/b0/...) from the header.
            set header [lrange $headerF0 1 end]
        }
        # Override the header with custom column names.
        if {[info exists metadata(columns)]} {
            set customColumnNames [split $metadata(columns) ,]
            set header [list \
                    {*}[lrange $customColumnNames \
                            0 [llength $customColumnNames]-1] \
                    {*}[lrange $header \
                            [llength $customColumnNames] end]]
        }
        $newTable configure -header $header

        $newTable initialize

        # Insert rows in the table.
        $newTable insert-rows $rows

        dict set tables $metadata(table) $newTable
        return $newTable
    }

    # Perform query $query and output the result to $channel.
    method perform-query {query {channel stdout}} {
        # For each row returned...
        set outputRecords {}
        [$self cget -database] eval $query results {
            set outputRecord {}
            set keys $results(*)
            foreach key $keys {
                lappend outputRecord $key $results($key)
            }
            lappend outputRecords $outputRecord
        }
        set sqawkOptions {}
        foreach option [$self info options] {
            dict set sqawkOptions $option [$self cget $option]
        }
        set output [$self Serialize [$self cget -outputformat] $outputRecords \
                $sqawkOptions]
        puts -nonewline $channel $output
    }
}
