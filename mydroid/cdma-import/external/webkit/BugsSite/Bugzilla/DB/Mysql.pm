# -*- Mode: perl; indent-tabs-mode: nil -*-
#
# The contents of this file are subject to the Mozilla Public
# License Version 1.1 (the "License"); you may not use this file
# except in compliance with the License. You may obtain a copy of
# the License at http://www.mozilla.org/MPL/
#
# Software distributed under the License is distributed on an "AS
# IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
# implied. See the License for the specific language governing
# rights and limitations under the License.
#
# The Original Code is the Bugzilla Bug Tracking System.
#
# The Initial Developer of the Original Code is Netscape Communications
# Corporation. Portions created by Netscape are
# Copyright (C) 1998 Netscape Communications Corporation. All
# Rights Reserved.
#
# Contributor(s): Dave Miller <davem00@aol.com>
#                 Gayathri Swaminath <gayathrik00@aol.com>
#                 Jeroen Ruigrok van der Werven <asmodai@wxs.nl>
#                 Dave Lawrence <dkl@redhat.com>
#                 Tomas Kopal <Tomas.Kopal@altap.cz>
#                 Max Kanat-Alexander <mkanat@bugzilla.org>

=head1 NAME

Bugzilla::DB::Mysql - Bugzilla database compatibility layer for MySQL

=head1 DESCRIPTION

This module overrides methods of the Bugzilla::DB module with MySQL specific
implementation. It is instantiated by the Bugzilla::DB module and should never
be used directly.

For interface details see L<Bugzilla::DB> and L<DBI>.

=cut

package Bugzilla::DB::Mysql;

use strict;

use Bugzilla::Error;

# This module extends the DB interface via inheritance
use base qw(Bugzilla::DB);

use constant REQUIRED_VERSION => '3.23.41';
use constant PROGRAM_NAME => 'MySQL';
use constant MODULE_NAME  => 'Mysql';
use constant DBD_VERSION  => '2.9003';

sub new {
    my ($class, $user, $pass, $host, $dbname, $port, $sock) = @_;

    # construct the DSN from the parameters we got
    my $dsn = "DBI:mysql:host=$host;database=$dbname";
    $dsn .= ";port=$port" if $port;
    $dsn .= ";mysql_socket=$sock" if $sock;
    
    my $self = $class->db_new($dsn, $user, $pass);

    # all class local variables stored in DBI derived class needs to have
    # a prefix 'private_'. See DBI documentation.
    $self->{private_bz_tables_locked} = "";

    bless ($self, $class);
    
    return $self;
}

# when last_insert_id() is supported on MySQL by lowest DBI/DBD version
# required by Bugzilla, this implementation can be removed.
sub bz_last_key {
    my ($self) = @_;

    my ($last_insert_id) = $self->selectrow_array('SELECT LAST_INSERT_ID()');

    return $last_insert_id;
}

sub sql_regexp {
    return "REGEXP";
}

sub sql_not_regexp {
    return "NOT REGEXP";
}

sub sql_limit {
    my ($self, $limit, $offset) = @_;

    if (defined($offset)) {
        return "LIMIT $offset, $limit";
    } else {
        return "LIMIT $limit";
    }
}

sub sql_string_concat {
    my ($self, @params) = @_;
    
    return 'CONCAT(' . join(', ', @params) . ')';
}

sub sql_fulltext_search {
    my ($self, $column, $text) = @_;

    return "MATCH($column) AGAINST($text)";
}

sub sql_istring {
    my ($self, $string) = @_;
    
    return $string;
}

sub sql_from_days {
    my ($self, $days) = @_;

    return "FROM_DAYS($days)";
}

sub sql_to_days {
    my ($self, $date) = @_;

    return "TO_DAYS($date)";
}

sub sql_date_format {
    my ($self, $date, $format) = @_;

    $format = "%Y.%m.%d %H:%i:%s" if !$format;
    
    return "DATE_FORMAT($date, " . $self->quote($format) . ")";
}

sub sql_interval {
    my ($self, $interval, $units) = @_;
    
    return "INTERVAL $interval $units";
}

sub sql_position {
    my ($self, $fragment, $text) = @_;

    # mysql 4.0.1 and lower do not support CAST
    # mysql 3.*.* had a case-sensitive INSTR
    # (checksetup has a check for unsupported versions)
    my $server_version = $self->bz_server_version;
    if ($server_version =~ /^3\./) {
        return "INSTR($text, $fragment)";
    } else {
        return "INSTR(CAST($text AS BINARY), CAST($fragment AS BINARY))";
    }
}

sub sql_group_by {
    my ($self, $needed_columns, $optional_columns) = @_;

    # MySQL allows to specify all columns as ANSI SQL requires, but also
    # allow you to specify just minimal subset to get unique result.
    # According to MySQL documentation, the less columns you specify
    # the faster the query runs.
    return "GROUP BY $needed_columns";
}


sub bz_lock_tables {
    my ($self, @tables) = @_;

    my $list = join(', ', @tables);
    # Check first if there was no lock before
    if ($self->{private_bz_tables_locked}) {
        ThrowCodeError("already_locked", { current => $self->{private_bz_tables_locked},
                                           new => $list });
    } else {
        $self->do('LOCK TABLE ' . $list); 
    
        $self->{private_bz_tables_locked} = $list;
    }
}

sub bz_unlock_tables {
    my ($self, $abort) = @_;
    
    # Check first if there was previous matching lock
    if (!$self->{private_bz_tables_locked}) {
        # Abort is allowed even without previous lock for error handling
        return if $abort;
        ThrowCodeError("no_matching_lock");
    } else {
        $self->do("UNLOCK TABLES");
    
        $self->{private_bz_tables_locked} = "";
    }
}

# As Bugzilla currently runs on MyISAM storage, which does not supprt
# transactions, these functions die when called.
# Maybe we should just ignore these calls for now, but as we are not
# using transactions in MySQL yet, this just hints the developers.
sub bz_start_transaction {
    die("Attempt to start transaction on DB without transaction support");
}

sub bz_commit_transaction {
    die("Attempt to commit transaction on DB without transaction support");
}

sub bz_rollback_transaction {
    die("Attempt to rollback transaction on DB without transaction support");
}


sub _bz_get_initial_schema {
    my ($self) = @_;
    return $self->_bz_build_schema_from_disk();
}

#####################################################################
# Database Setup
#####################################################################

sub bz_setup_database {
    my ($self) = @_;

    # Figure out if any existing tables are of type ISAM and convert them
    # to type MyISAM if so.  ISAM tables are deprecated in MySQL 3.23,
    # which Bugzilla now requires, and they don't support more than 16
    # indexes per table, which Bugzilla needs.
    my $sth = $self->prepare("SHOW TABLE STATUS");
    $sth->execute;
    my @isam_tables = ();
    while (my ($name, $type) = $sth->fetchrow_array) {
        push(@isam_tables, $name) if $type eq "ISAM";
    }

    if(scalar(@isam_tables)) {
        print "One or more of the tables in your existing MySQL database are\n"
              . "of type ISAM. ISAM tables are deprecated in MySQL 3.23 and\n"
              . "don't support more than 16 indexes per table, which \n"
              . "Bugzilla needs.\n  Converting your ISAM tables to type"
              . " MyISAM:\n\n";
        foreach my $table (@isam_tables) {
            print "Converting table $table... ";
            $self->do("ALTER TABLE $table TYPE = MYISAM");
            print "done.\n";
        }
        print "\nISAM->MyISAM table conversion done.\n\n";
    }

    # Versions of Bugzilla before the existence of Bugzilla::DB::Schema did 
    # not provide explicit names for the table indexes. This means
    # that our upgrades will not be reliable, because we look for the name
    # of the index, not what fields it is on, when doing upgrades.
    # (using the name is much better for cross-database compatibility 
    # and general reliability). It's also very important that our
    # Schema object be consistent with what is on the disk.
    #
    # While we're at it, we also fix some inconsistent index naming
    # from the original checkin of Bugzilla::DB::Schema.

    # We check for the existence of a particular "short name" index that
    # has existed at least since Bugzilla 2.8, and probably earlier.
    # For fixing the inconsistent naming of Schema indexes,
    # we also check for one of those inconsistently-named indexes.
    my @tables = $self->bz_table_list_real();
    if ( scalar(@tables) && 
         ($self->bz_index_info_real('bugs', 'assigned_to') ||
          $self->bz_index_info_real('flags', 'flags_bidattid_idx')) )
    {

        # This is a check unrelated to the indexes, to see if people are
        # upgrading from 2.18 or below, but somehow have a bz_schema table
        # already. This only happens if they have done a mysqldump into
        # a database without doing a DROP DATABASE first.
        # We just do the check here since this check is a reliable way
        # of telling that we are upgrading from a version pre-2.20.
        if (grep($_ eq 'bz_schema', $self->bz_table_list_real())) {
            die("\nYou are upgrading from a version before 2.20, but the"
              . " bz_schema\ntable already exists. This means that you"
              . " restored a mysqldump into\nthe Bugzilla database without"
              . " first dropping the already-existing\nBugzilla database,"
              . " at some point. Whenever you restore a Bugzilla\ndatabase"
              . " backup, you must always drop the entire database first.\n\n"
              . "Please drop your Bugzilla database and restore it from a"
              . " backup that\ndoes not contain the bz_schema table. If for"
              . " some reason you cannot\ndo this, you can connect to your"
              . " MySQL database and drop the bz_schema\ntable, as a last"
              . " resort.\n");
        }

        my $bug_count = $self->selectrow_array("SELECT COUNT(*) FROM bugs");
        # We estimate one minute for each 3000 bugs, plus 3 minutes just
        # to handle basic MySQL stuff.
        my $rename_time = int($bug_count / 3000) + 3;
        # And 45 minutes for every 15,000 attachments, per some experiments.
        my ($attachment_count) = 
            $self->selectrow_array("SELECT COUNT(*) FROM attachments");
        $rename_time += int(($attachment_count * 45) / 15000);
        # If we're going to take longer than 5 minutes, we let the user know
        # and allow them to abort.
        if ($rename_time > 5) {
            print "\nWe are about to rename old indexes.\n"
                  . "The estimated time to complete renaming is "
                  . "$rename_time minutes.\n"
                  . "You cannot interrupt this action once it has begun.\n"
                  . "If you would like to cancel, press Ctrl-C now..."
                  . " (Waiting 45 seconds...)\n\n";
            # Wait 45 seconds for them to respond.
            sleep(45);
        }
        print "Renaming indexes...\n";

        # We can't be interrupted, because of how the "if"
        # works above.
        local $SIG{INT}  = 'IGNORE';
        local $SIG{TERM} = 'IGNORE';
        local $SIG{PIPE} = 'IGNORE';

        # Certain indexes had names in Schema that did not easily conform
        # to a standard. We store those names here, so that they
        # can be properly renamed.
        # Also, sometimes an old mysqldump would incorrectly rename
        # unique indexes to "PRIMARY", so we address that here, also.
        my $bad_names = {
            # 'when' is a possible leftover from Bugzillas before 2.8
            bugs_activity => ['when', 'bugs_activity_bugid_idx',
                'bugs_activity_bugwhen_idx'],
            cc => ['PRIMARY'],
            longdescs => ['longdescs_bugid_idx',
               'longdescs_bugwhen_idx'],
            flags => ['flags_bidattid_idx'],
            flaginclusions => ['flaginclusions_tpcid_idx'],
            flagexclusions => ['flagexclusions_tpc_id_idx'],
            keywords => ['PRIMARY'],
            milestones => ['PRIMARY'],
            profiles_activity => ['profiles_activity_when_idx'],
            group_control_map => ['group_control_map_gid_idx', 'PRIMARY'],
            user_group_map => ['PRIMARY'],
            group_group_map => ['PRIMARY'],
            email_setting => ['PRIMARY'],
            bug_group_map => ['PRIMARY'],
            category_group_map => ['PRIMARY'],
            watch => ['PRIMARY'],
            namedqueries => ['PRIMARY'],
            series_data => ['PRIMARY'],
            # series_categories is dealt with below, not here.
        };

        # The series table is broken and needs to have one index
        # dropped before we begin the renaming, because it had a
        # useless index on it that would cause a naming conflict here.
        if (grep($_ eq 'series', @tables)) {
            my $dropname;
            # This is what the bad index was called before Schema.
            if ($self->bz_index_info_real('series', 'creator_2')) {
                $dropname = 'creator_2';
            }
            # This is what the bad index is called in Schema.
            elsif ($self->bz_index_info_real('series', 'series_creator_idx')) {
                    $dropname = 'series_creator_idx';
            }
            $self->bz_drop_index_raw('series', $dropname) if $dropname;
        }

        # The email_setting table also had the same problem.
        if( grep($_ eq 'email_setting', @tables) 
            && $self->bz_index_info_real('email_setting', 
                                         'email_settings_user_id_idx') ) 
        {
            $self->bz_drop_index_raw('email_setting', 
                                     'email_settings_user_id_idx');
        }

        # Go through all the tables.
        foreach my $table (@tables) {
            # Will contain the names of old indexes as keys, and the 
            # definition of the new indexes as a value. The values
            # include an extra hash key, NAME, with the new name of 
            # the index.
            my %rename_indexes;
            # And go through all the columns on each table.
            my @columns = $self->bz_table_columns_real($table);

            # We also want to fix the silly naming of unique indexes
            # that happened when we first checked-in Bugzilla::DB::Schema.
            if ($table eq 'series_categories') {
                # The series_categories index had a nonstandard name.
                push(@columns, 'series_cats_unique_idx');
            } 
            elsif ($table eq 'email_setting') { 
                # The email_setting table had a similar problem.
                push(@columns, 'email_settings_unique_idx');
            }
            else {
                push(@columns, "${table}_unique_idx");
            }
            # And this is how we fix the other inconsistent Schema naming.
            push(@columns, @{$bad_names->{$table}})
                if (exists $bad_names->{$table});
            foreach my $column (@columns) {
                # If we have an index named after this column, it's an 
                # old-style-name index.
                if (my $index = $self->bz_index_info_real($table, $column)) {
                    # Fix the name to fit in with the new naming scheme.
                    $index->{NAME} = $table . "_" .
                                     $index->{FIELDS}->[0] . "_idx";
                    print "Renaming index $column to " 
                          . $index->{NAME} . "...\n";
                    $rename_indexes{$column} = $index;
                } # if
            } # foreach column

            my @rename_sql = $self->_bz_schema->get_rename_indexes_ddl(
                $table, %rename_indexes);
            $self->do($_) foreach (@rename_sql);

        } # foreach table
    } # if old-name indexes


    # And now we create the tables and the Schema object.
    $self->SUPER::bz_setup_database();


    # The old timestamp fields need to be adjusted here instead of in
    # checksetup. Otherwise the UPDATE statements inside of bz_add_column
    # will cause accidental timestamp updates.
    # The code that does this was moved here from checksetup.

    # 2002-08-14 - bbaetz@student.usyd.edu.au - bug 153578
    # attachments creation time needs to be a datetime, not a timestamp
    my $attach_creation = 
        $self->bz_column_info("attachments", "creation_ts");
    if ($attach_creation && $attach_creation->{TYPE} =~ /^TIMESTAMP/i) {
        print "Fixing creation time on attachments...\n";

        my $sth = $self->prepare("SELECT COUNT(attach_id) FROM attachments");
        $sth->execute();
        my ($attach_count) = $sth->fetchrow_array();

        if ($attach_count > 1000) {
            print "This may take a while...\n";
        }
        my $i = 0;

        # This isn't just as simple as changing the field type, because
        # the creation_ts was previously updated when an attachment was made
        # obsolete from the attachment creation screen. So we have to go
        # and recreate these times from the comments..
        $sth = $self->prepare("SELECT bug_id, attach_id, submitter_id " .
                               "FROM attachments");
        $sth->execute();

        # Restrict this as much as possible in order to avoid false 
        # positives, and keep the db search time down
        my $sth2 = $self->prepare("SELECT bug_when FROM longdescs 
                                    WHERE bug_id=? AND who=? 
                                          AND thetext LIKE ?
                                 ORDER BY bug_when " . $self->sql_limit(1));
        while (my ($bug_id, $attach_id, $submitter_id) 
                  = $sth->fetchrow_array()) 
        {
            $sth2->execute($bug_id, $submitter_id, 
                "Created an attachment (id=$attach_id)%");
            my ($when) = $sth2->fetchrow_array();
            if ($when) {
                $self->do("UPDATE attachments " .
                             "SET creation_ts='$when' " .
                           "WHERE attach_id=$attach_id");
            } else {
                print "Warning - could not determine correct creation"
                      . " time for attachment $attach_id on bug $bug_id\n";
            }
            ++$i;
            print "Converted $i of $attach_count attachments\n" if !($i % 1000);
        }
        print "Done - converted $i attachments\n";

        $self->bz_alter_column("attachments", "creation_ts", 
                               {TYPE => 'DATETIME', NOTNULL => 1});
    }

    # 2004-08-29 - Tomas.Kopal@altap.cz, bug 257303
    # Change logincookies.lastused type from timestamp to datetime
    my $login_lastused = $self->bz_column_info("logincookies", "lastused");
    if ($login_lastused && $login_lastused->{TYPE} =~ /^TIMESTAMP/i) {
        $self->bz_alter_column('logincookies', 'lastused', 
                               { TYPE => 'DATETIME',  NOTNULL => 1});
    }

    # 2005-01-17 - Tomas.Kopal@altap.cz, bug 257315
    # Change bugs.delta_ts type from timestamp to datetime 
    my $bugs_deltats = $self->bz_column_info("bugs", "delta_ts");
    if ($bugs_deltats && $bugs_deltats->{TYPE} =~ /^TIMESTAMP/i) {
        $self->bz_alter_column('bugs', 'delta_ts', 
                               {TYPE => 'DATETIME', NOTNULL => 1});
    }

}


sub bz_enum_initial_values {
    my ($self, $enum_defaults) = @_;
    my %enum_values = %$enum_defaults;
    # Get a complete description of the 'bugs' table; with DBD::MySQL
    # there isn't a column-by-column way of doing this.  Could use
    # $dbh->column_info, but it would go slower and we would have to
    # use the undocumented mysql_type_name accessor to get the type
    # of each row.
    my $sth = $self->prepare("DESCRIBE bugs");
    $sth->execute();
    # Look for the particular columns we are interested in.
    while (my ($thiscol, $thistype) = $sth->fetchrow_array()) {
        if (defined $enum_values{$thiscol}) {
            # this is a column of interest.
            my @value_list;
            if ($thistype and ($thistype =~ /^enum\(/)) {
                # it has an enum type; get the set of values.
                while ($thistype =~ /'([^']*)'(.*)/) {
                    push(@value_list, $1);
                    $thistype = $2;
                }
            }
            if (@value_list) {
                # record the enum values found.
                $enum_values{$thiscol} = \@value_list;
            }
        }
    }

    return \%enum_values;
}

#####################################################################
# MySQL-specific Database-Reading Methods
#####################################################################

=begin private

=head1 MYSQL-SPECIFIC DATABASE-READING METHODS

These methods read information about the database from the disk,
instead of from a Schema object. They are only reliable for MySQL 
(see bug 285111 for the reasons why not all DBs use/have functions
like this), but that's OK because we only need them for 
backwards-compatibility anyway, for versions of Bugzilla before 2.20.

=over 4

=item C<bz_column_info_real($table, $column)>

 Description: Returns an abstract column definition for a column
              as it actually exists on disk in the database.
 Params:      $table - The name of the table the column is on.
              $column - The name of the column you want info about.
 Returns:     An abstract column definition.
              If the column does not exist, returns undef.

=cut

sub bz_column_info_real {
    my ($self, $table, $column) = @_;

    # DBD::mysql does not support selecting a specific column,
    # so we have to get all the columns on the table and find 
    # the one we want.
    my $info_sth = $self->column_info(undef, undef, $table, '%');

    # Don't use fetchall_hashref as there's a Win32 DBI bug (292821)
    my $col_data;
    while ($col_data = $info_sth->fetchrow_hashref) {
        last if $col_data->{'COLUMN_NAME'} eq $column;
    }

    if (!defined $col_data) {
        return undef;
    }
    return $self->_bz_schema->column_info_to_column($col_data);
}

=item C<bz_index_info_real($table, $index)>

 Description: Returns information about an index on a table in the database.
 Params:      $table = name of table containing the index
              $index = name of an index
 Returns:     An abstract index definition, always in hashref format.
              If the index does not exist, the function returns undef.
=cut
sub bz_index_info_real {
    my ($self, $table, $index) = @_;

    my $sth = $self->prepare("SHOW INDEX FROM $table");
    $sth->execute;

    my @fields;
    my $index_type;
    # $raw_def will be an arrayref containing the following information:
    # 0 = name of the table that the index is on
    # 1 = 0 if unique, 1 if not unique
    # 2 = name of the index
    # 3 = seq_in_index (The order of the current field in the index).
    # 4 = Name of ONE column that the index is on
    # 5 = 'Collation' of the index. Usually 'A'.
    # 6 = Cardinality. Either a number or undef.
    # 7 = sub_part. Usually undef. Sometimes 1.
    # 8 = "packed". Usually undef.
    # MySQL 3
    # -------
    # 9 = comments. Usually an empty string. Sometimes 'FULLTEXT'.
    # MySQL 4
    # -------
    # 9 = Null. Sometimes undef, sometimes 'YES'.
    # 10 = Index_type. The type of the index. Usually either 'BTREE' or 'FULLTEXT'
    # 11 = 'Comment.' Usually undef.
    my $is_mysql3 = ($self->bz_server_version() =~ /^3/);
    my $index_type_loc = $is_mysql3 ? 9 : 10;
    while (my $raw_def = $sth->fetchrow_arrayref) {
        if ($raw_def->[2] eq $index) {
            push(@fields, $raw_def->[4]);
            # No index can be both UNIQUE and FULLTEXT, that's why
            # this is written this way.
            $index_type = $raw_def->[1] ? '' : 'UNIQUE';
            $index_type = $raw_def->[$index_type_loc] eq 'FULLTEXT'
                ? 'FULLTEXT' : $index_type;
        }
    }

    my $retval;
    if (scalar(@fields)) {
        $retval = {FIELDS => \@fields, TYPE => $index_type};
    }
    return $retval;
}

=item C<bz_index_list_real($table)>

 Description: Returns a list of index names on a table in 
              the database, as it actually exists on disk.
 Params:      $table - The name of the table you want info about.
 Returns:     An array of index names.

=cut

sub bz_index_list_real {
    my ($self, $table) = @_;
    my $sth = $self->prepare("SHOW INDEX FROM $table");
    # Column 3 of a SHOW INDEX statement contains the name of the index.
    return @{ $self->selectcol_arrayref($sth, {Columns => [3]}) };
}

#####################################################################
# MySQL-Specific "Schema Builder"
#####################################################################

=back

=head1 MYSQL-SPECIFIC "SCHEMA BUILDER"

MySQL needs to be able to read in a legacy database (from before 
Schema existed) and create a Schema object out of it. That's what
this code does.

=end private

=cut

# This sub itself is actually written generically, but the subroutines
# that it depends on are database-specific. In particular, the
# bz_column_info_real function would be very difficult to create
# properly for any other DB besides MySQL.
sub _bz_build_schema_from_disk {
    my ($self) = @_;

    print "Building Schema object from database...\n";

    my $schema = $self->_bz_schema->get_empty_schema();

    my @tables = $self->bz_table_list_real();
    foreach my $table (@tables) {
        $schema->add_table($table);
        my @columns = $self->bz_table_columns_real($table);
        foreach my $column (@columns) {
            my $type_info = $self->bz_column_info_real($table, $column);
            $schema->set_column($table, $column, $type_info);
        }

        my @indexes = $self->bz_index_list_real($table);
        foreach my $index (@indexes) {
            unless ($index eq 'PRIMARY') {
                my $index_info = $self->bz_index_info_real($table, $index);
                ($index_info = $index_info->{FIELDS}) 
                    if (!$index_info->{TYPE});
                $schema->set_index($table, $index, $index_info);
            }
        }
    }

    return $schema;
}
1;
