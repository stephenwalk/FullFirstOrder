import os
import sys
from subprocess import call

f = file('output_collection.txt','w+')
for i in range(1, 55):
    f_output = open('output' + str(i) +'.txt')
    all_lines = f_output.readlines()
    f.write('Case:' + str(i) + '\n')
    for eachline in all_lines:
        tmp_line = eachline.upper()
        f.write(tmp_line)
    f_output.close()
f.close()