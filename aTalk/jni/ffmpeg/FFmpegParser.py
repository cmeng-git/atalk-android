from argparse import ArgumentParser
import os
import re

__author__ = 'Ilja Kosynkin'


def get_white_symbols_from_line(line):
    res = ""

    for sym in line:
        if sym == '\t' or sym == ' ':
            res += sym
        else:
            break

    return res


files_set = ['ffmpeg_opt.c', 'ffmpeg_filter.c']

parser = ArgumentParser()
parser.add_argument("-p", "--prefix", dest="prefix",
                    help="Prefix to directory that contains FFmpeg in case script is not launched from it")

parser.add_argument("-f", "--files", dest="files", type=str, nargs='+',
                    help='Files that you would like to add to default set of files')

parser.add_argument("-d", "--directory", dest="dir",
                    help="Directory to which you would like to write processed files (default is \"processed\")",
                    default="processed")

parser.add_argument("-fjni", "--jni-file", dest="jni",
                    help="Java file in which you have your JNI for FFmpeg. Will be used to adjust videokit.c")

args = parser.parse_args()

out_dir = args.dir
if out_dir is None:
    out_dir = ""
elif out_dir[-1] != '/':
    out_dir += '/'

if not os.path.exists(out_dir):
    os.makedirs(out_dir)

prefix = args.prefix
if prefix is None:
    prefix = ""
elif prefix[-1] != '/':
    prefix += '/'

if args.files is not None:
    for f in args.files:
        files_set.append(f)

help_file = open(out_dir + 'readme.txt', 'w+')
help_file.write(
    "This file contains corresponding codes to each specified source file. "
    "Every file have assigned leading code from 1 to N and MMM sub-code"
    " according to error. \nAlso every code have according to it number of line.\n\n")

input_file = open(prefix + 'cmdutils.h')
out = open(out_dir + 'cmdutils.h', 'w+')

pass_headers = False
pass_variables = False
for line in input_file.readlines():
    out.write(line)
    if not pass_variables and line.startswith("extern"):
        pass_variables = True
        out.write("\n\nextern int loglevel;\nextern jmp_buf env_buf;\n\n")
    elif not pass_headers and line.startswith("#define"):
        pass_headers = True
        out.write("\n\n#include <setjmp.h>\n#include <stdint.h>\n\n#include <android/log.h>\n#include \"logjam.h\"\n\n")

out.close()

input_file = open(prefix + 'cmdutils.c')
out = open(out_dir + 'cmdutils.c', 'w+')

files_counter = 1
regex_abort = re.compile(r"abort\(\)", re.IGNORECASE)
regex_exit = re.compile(r"exit\([0-9]*\)", re.IGNORECASE)
regex_exit2 = re.compile(r"exit_program\([0-9]*\)", re.IGNORECASE)
regex_exit3 = re.compile(r"exit_program", re.IGNORECASE)
regex_print = re.compile(r"print_error", re.IGNORECASE)

pass_static = False
pass_exit = False

current_counter = 0
help_file.write("CMDUTILS.C LEAD CODE: %d \n" % files_counter)
current_line = 1
for line in input_file.readlines():
    if not pass_static and line.startswith("static"):
        pass_static = True
	out.write(line)
        out.write("\n\njmp_buf env_buf;\n\n")
    elif not pass_exit and "program_exit(ret);" in line:
        pass_exit = True
	out.write(line)
        out.write("\t\tlongjmp(env_buf, ret);\n")
        continue
    elif "abort()" in line:
        exit_code = files_counter * 1000 + current_counter
        help_file.write("\tCode: %d, line: %d \n" % (exit_code, current_line))
        out.write(regex_abort.sub("exit_program(%i)" % exit_code, line))
        current_counter += 1
    elif ("exit(" in line or "exit_program(" in line) and "_exit" not in line and "void" not in line:
        exit_code = files_counter * 1000 + current_counter
        help_file.write("\tCode: %d, line: %d \n" % (exit_code, current_line))
        new_line = regex_exit.sub('exit_program(%d)' % exit_code, line)
        if new_line == line:
            new_line = regex_exit2.sub('exit_program(%d)' % exit_code, line)

        out.write(new_line)
        current_counter += 1   
    elif "print_error" in line and "void" not in line:
	new_line = regex_print.sub('LOGI', line)
        white_spaces = get_white_symbols_from_line(line)
	out.write(white_spaces + "if (loglevel > 0) \n")
	out.write(white_spaces + "\t" + new_line)
    elif "av_log(" in line and "//" not in line:
        white_spaces = get_white_symbols_from_line(line)
        final_line = white_spaces
        tag = line.split(',')[1]
        if 'ERROR' in tag or 'FATAL' in tag:
            final_line += "if (loglevel > 0) "
        else:
            final_line += "if (loglevel == 2) "

        if line.strip()[-1] == '\\':
            final_line += '\t \\\n'
        else:
            final_line += '\n'

        second_comma_position = [m.start() for m in re.finditer(',', line)][1]
        final_line += white_spaces + '\t' + "LOGI(" + line[second_comma_position + 2:]
        out.write(final_line)
    else:
        out.write(line)
    current_line += 1

out.close()

input_file = open(prefix + 'ffmpeg.c')
out = open(out_dir + 'ffmpeg.c', 'w+')

current_counter = 0
files_counter += 1

pass_static = False
pass_main = False
pass_bracket = False
help_file.write("\nFFMPEG.C LEAD CODE: %d \n" % files_counter)
current_line = 1
for line in input_file.readlines():
    if not pass_static and line.startswith("static"):
        pass_static = True
	out.write(line)
        out.write("\n\nint loglevel = 0;\nint return_code = -1;\njmp_buf env_buf;\n\n")
    elif not pass_main and "int main(int argc, char **argv)" in line:
	pass_main = True
	out.write("int main(int loglevel, int argc, char **argv)\n")
    elif pass_main and not pass_bracket:
	pass_bracket = True
	out.write(line)
	out.write("\treceived_sigterm = 0;\n\treceived_nb_signals = 0;\n\n\ttranscode_init_done = 0;\n\tffmpeg_exited = 0;\n\tmain_return_code = 0;\n\trun_as_daemon  = 0;\n\n\tnb_frames_dup = 0;\n\tnb_frames_drop = 0;\n\tnb_input_streams = 0;\n\tnb_input_files   = 0;\n\tnb_output_streams = 0;\n\tnb_output_files   = 0;\n\tnb_filtergraphs = 0;\n\n")
	
	out.write("\tint jmpret = setjmp(env_buf);\n\tif (jmpret != 0) {\n\t\treturn jmpret;\n\t}\n\n")
    elif pass_main and "exit_program(received_nb_signals ? 255 : main_return_code);" in line:
        out.write(regex_exit3.sub("ffmpeg_cleanup", line))
    elif "abort()" in line:
        exit_code = files_counter * 1000 + current_counter
        help_file.write("\tCode: %d, line: %d \n" % (exit_code, current_line))
        out.write(regex_abort.sub("exit_program(%i)" % exit_code, line))
        current_counter += 1
    elif ("exit(" in line or "exit_program(" in line) and "_exit" not in line and "void" not in line:
        exit_code = files_counter * 1000 + current_counter
        help_file.write("\tCode: %d, line: %d \n" % (exit_code, current_line))
        new_line = regex_exit.sub('exit_program(%d)' % exit_code, line)
        if new_line == line:
            new_line = regex_exit2.sub('exit_program(%d)' % exit_code, line)

        out.write(new_line)
        current_counter += 1
    elif "print_error" in line and "void" not in line:
	new_line = regex_print.sub('LOGI', line)
        white_spaces = get_white_symbols_from_line(line)
	out.write(white_spaces + "if (loglevel > 0) \n")
	out.write(white_spaces + "\t" + new_line)
    elif "av_log(" in line and "//" not in line:
        white_spaces = get_white_symbols_from_line(line)
        final_line = white_spaces
        tag = line.split(',')[1]
        if 'ERROR' in tag or 'FATAL' in tag:
            final_line += "if (loglevel > 0) "
        else:
            final_line += "if (loglevel == 2) "

        if line.strip()[-1] == '\\':
            final_line += '\t \\\n'
        else:
            final_line += '\n'

        second_comma_position = [m.start() for m in re.finditer(',', line)][1]
        final_line += white_spaces + '\t' + "LOGI(" + line[second_comma_position + 2:]
        out.write(final_line)
    else:
        out.write(line)
    current_line += 1

out.close()

for name in files_set:
    input_file = open(prefix + name)
    out = open(out_dir + name, 'w+')

    current_counter = 0
    files_counter += 1

    parsed_name = name.split('\\')[-1]
    help_file.write("\n%s LEAD CODE: %d \n" % (parsed_name.upper(), files_counter))
    current_line = 1

    for line in input_file.readlines():
        if "abort()" in line:
            exit_code = files_counter * 1000 + current_counter
            help_file.write("\tCode: %d, line: %d \n" % (exit_code, current_line))
            out.write(regex_abort.sub("exit_program(%i)" % exit_code, line))
            current_counter += 1
        elif ("exit(" in line or "exit_program(" in line) and "_exit" not in line and "void" not in line:
            exit_code = files_counter * 1000 + current_counter
            help_file.write("\tCode: %d, line: %d \n" % (exit_code, current_line))
            new_line = regex_exit.sub('exit_program(%d)' % exit_code, line)
            if new_line == line:
                new_line = regex_exit2.sub('exit_program(%d)' % exit_code, line)

            out.write(new_line)
            current_counter += 1
    	elif "print_error" in line and "void" not in line:
	    new_line = regex_print.sub('LOGI', line)
            white_spaces = get_white_symbols_from_line(line)
	    out.write(white_spaces + "if (loglevel > 0) \n")
	    out.write(white_spaces + "\t" + new_line)
    	elif "av_log(" in line and "//" not in line:
            white_spaces = get_white_symbols_from_line(line)
            final_line = white_spaces
            tag = line.split(',')[1]
            if 'ERROR' in tag or 'FATAL' in tag:
                final_line += "if (loglevel > 0) "
            else:
                final_line += "if (loglevel == 2) "

            if line.strip()[-1] == '\\':
                final_line += '\t \\\n'
            else:
                final_line += '\n'

            second_comma_position = [m.start() for m in re.finditer(',', line)][1]
            final_line += white_spaces + '\t' + "LOGI(" + line[second_comma_position + 2:]
            out.write(final_line)
        else:
            out.write(line)
        current_line += 1
    out.close()
