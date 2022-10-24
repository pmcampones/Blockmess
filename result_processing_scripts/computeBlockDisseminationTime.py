from sys import argv
import pandas as pd
from functools import reduce
import csv


def get_block_dissemination_times(files):
    replicas_csv = [pd.read_csv(file, index_col='BlockID') for file in files]
    replicas_no_dup = [df.groupby('BlockID').first() for df in replicas_csv]
    block_arrivals = [df.drop(columns=['Num Txs', 'Block Size']) for df in replicas_no_dup]
    arrival_times = reduce(lambda lft, rgt: pd.merge(lft, rgt, left_index=True, right_index=True), block_arrivals)
    proposal_times = arrival_times.min(axis='columns')
    last_arrival = arrival_times.max(axis='columns')
    dissemination_times = last_arrival - proposal_times
    return dissemination_times.rename('Dissemination Times')


def record_dissemination_times(dissemination_times, output_pathname):
    dissemination_times.to_csv(output_pathname, quoting=csv.QUOTE_ALL)


def main():
    if len(argv) < 3:
        print('./computeDisseminationTime <Files Pointer File> <Dissemination Times Output Pathname>')
        print('Files Pointer File: File containing the pathname of the files containing the record of the disseminated '
              'blocks.')
        print('Dissemination Times Output Pathname: Output pathname where the dissemination times of logs will be logged.')
    else:
        with open(argv[1], 'r') as fin:
            files = fin.readlines()
            files = [line.strip('\n') for line in files]
        dissemination_times = get_block_dissemination_times(files)
        record_dissemination_times(dissemination_times, argv[2])
        print(dissemination_times)

if __name__ == "__main__":
    main()
