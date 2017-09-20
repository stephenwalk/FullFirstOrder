import os
import sys
from subprocess import call

f = file('test_result.txt','w+')
for i in range(1, 55):
    os.rename('input' + str(i) + '.txt', 'input.txt')
    call(['python', 'homework.py'])
    f_output = open('output.txt')
    all_lines = f_output.readlines()
    f.write('Case:' + str(i) + '\n')
    for eachline in all_lines:
        tmp_line = eachline.upper()
        f.write(tmp_line)
    f_output.close()
    os.rename('input.txt', 'input' + str(i) + '.txt')
f.close
