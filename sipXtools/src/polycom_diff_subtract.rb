#!/usr/bin/env ruby
#
# Copyright (C) 2008 Nortel, certain elements licensed under a Contributor Agreement.  
# Contributors retain copyright to elements licensed under a Contributor Agreement.
# Licensed to the User under the LGPL license.
#
###################################################

# Usage: polycom_ini_diff.rb SubtractFile [DiffFile]
# Using the "DiffFile" output from the polycom_ini_diff.rb tool, this script isolates the
# "D=>" differences, and filters out the parameters identified in the SubtractFile.  The
# SubtractFile is expected to contain individual lines that begine with each parameter ID
# to be filtered out.  Any text "," and after is discarded, so this file can be a CSV where 
# column 1 contains the parameter ID.  If DiffFile in not specified as a command-line 
# argument, then STDIN is used instead.

ITEM_DELIM="=>"

if 2 < ARGV.length || 0 == ARGV.length 
   STDERR.puts "Usage: polycom_ini_diff.rb SubtractFile [DiffFile]"
   Process.exit(1)
end

# Setup the two required inputs.
subtract_input=File.open(ARGV[0], "r")
if 2 == ARGV.length 
   diff_input = File.open(ARGV[1], "r") 
else
   diff_input = STDIN
end

# Collect the "D=>" differences from DiffFile.  
different_lines=[]
diff_input.readlines.each do |line|
   index = line.index("D#{ITEM_DELIM}")
   if 0 == index
      different_lines.push(line)
   end
end 
before_count = different_lines.length

# Collect the parameters to remove from SubtractFile.
subtract_parameters=[]
subtract_input.readlines.each do |line|
   index = line.index("#")
   if nil == index || 0 != index
      parameter = line.split(",")[0].chomp
      if nil != parameter && 0 != parameter.length
         subtract_parameters.push(parameter)
      end
   end
end 

# Do the subtraction.
subtract_count = 0
subtract_parameters.each do |subtract_line|
   (0..different_lines.length-1).each do |i|
      if nil != different_lines[i] && different_lines[i].split(" ")[1] == subtract_line
         different_lines[i] = nil
         subtract_count = subtract_count + 1
      end
   end
end

# Print the remaining.
remaining_count = 0
different_lines.each do |line|
   if nil != line
      puts line
      remaining_count = remaining_count + 1
   end
end

puts "# Before    : #{"%3d" % before_count}"
puts "# Subtracted: #{"%3d" % subtract_count}"
puts "# Remaining : #{"%3d" % remaining_count}"

