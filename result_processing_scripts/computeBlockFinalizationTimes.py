from sys import argv
import pandas as pd
from functools import reduce
import csv

def get_block_finalization_latency(unfinalized_blocks_files, finalized_blocks_files):
    '''
    Finds the average finalization latency for all blocks common to all nodes.
    '''
    block_proposal_times = get_block_arrival_times(unfinalized_blocks_files)
    block_finalization_times = get_block_finalization_times(finalized_blocks_files).rename('Average Block Finalization Time')
    finalization_latency = (block_finalization_times - block_proposal_times).dropna().astype(int).rename('Elapsed Finalization Latency')
    return pd.merge(finalization_latency, block_finalization_times, left_index=True, right_index=True)


def get_block_arrival_times(files):
    '''
    Finds the average block arrival time for every block proposed and common to all nodes.
    '''
    unfinalized = get_and_filter_blocks(files)
    block_arrivals = [df.drop(columns=['Num Txs', 'Block Size']) for df in unfinalized]
    arrival_times = reduce(lambda lft, rgt: pd.merge(lft, rgt, left_index=True, right_index=True), block_arrivals)
    return arrival_times.mean(axis='columns').astype(int)


def get_block_finalization_times(files):
    """
    Finds the average block arrival time for every block finalized and common to all nodes.
    """
    finalized = get_and_filter_blocks(files)
    finalization_times = reduce(lambda lft, rgt: pd.merge(lft, rgt, left_index=True, right_index=True), finalized)
    return finalization_times.mean(axis='columns').astype(int)
    
    
def get_and_filter_blocks(files):
    '''
    Reads the CSV in the given pathnames and filters 'possible' duplicates
    There should exist no duplicates... but I've wrong about that before'
    '''
    blocks_csv = [pd.read_csv(file, index_col='BlockID') for file in files]
    return [df.groupby('BlockID').first() for df in blocks_csv]

def record_dissemination_times(dissemination_times, output_pathname):
    '''
    Records the dataframe received as parameter in the output_pathname
    '''
    dissemination_times.to_csv(output_pathname, quoting=csv.QUOTE_ALL)


def read_pointer_files(pointer_file):
    '''
    Reads the files being referenced by the pointer file received as parameter.
    '''
    with open(pointer_file, 'r') as fin:
        files = fin.readlines()
        files = [line.strip('\n') for line in files]
    return files


def main():
    if len(argv) < 4:
        print('./computeBlockFinalizationTimes <Unfinalized Blocks Pointer File> <Finalized Blocks Pointer File> <Dissemination Times Output Pathname>')
        print('Unfinalized Blocks Pointer File: File containing the pathname of the files containing the record of the disseminated unfinalized blocks.')
        print('Finalized Blocks Pointer File: File containing the pathname of the files containing the record of the finalized blocks in each node.')
        print('Output Pathname: Output pathname where the finalization times will be logged.')
    else:
        unfinalized_blocks_files = read_pointer_files(argv[1])
        finalized_blocks_files = read_pointer_files(argv[2])
        finalization_latencies = get_block_finalization_latency(unfinalized_blocks_files, finalized_blocks_files)
        record_dissemination_times(finalization_latencies, argv[3])
        print(finalization_latencies)


if __name__ == "__main__":
    main()
