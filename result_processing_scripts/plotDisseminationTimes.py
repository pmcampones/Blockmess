from sys import argv
import pandas as pd
import matplotlib.pyplot as plt
import statistics


def gen_dissemination_times_boxplot(block_sizes, dissemination_times, output_pathname):
    '''
    Generate a graph presenting the dissemination time of blocks by varying the block size.
    '''
    median = [statistics.median(times) for times in dissemination_times]
    axis_plt = list(range(1, len(block_sizes) + 1))
    plt.boxplot(dissemination_times, labels=block_sizes, showfliers=False)
    plt.plot(axis_plt, median, 'o--', markersize=3, linewidth=1)
    plt.grid(color='grey', axis='y', linestyle='-', linewidth=0.25, alpha=0.5)
    plt.xlabel('Block Size (KB)')
    plt.ylabel('Dissemination Time (ms)')
    plt.savefig(output_pathname)


def get_dissemination_times_from_file(file):
    return list(pd.read_csv(file, index_col='BlockID')['Dissemination Times'])


def main():
    if len(argv) < 3:
        print('./plotDisseminationTimes <Dissemination Times Pointer File> <Graph Output Pathname>')
        print('Dissemination Times Pointer File: File containing the pathname of the files containing the average block dissemination times for a given run indexed by the block sizes.')
        print('Graph Output Pathname: Output pathname for the dissemination time graph.')
    else:
        dissemination_pointer = pd.read_csv(argv[1])
        block_sizes = list(dissemination_pointer['BlockSize'])
        dissemination_files = list(dissemination_pointer['Pointed File'])
        dissemination_times = [get_dissemination_times_from_file(file) for file in dissemination_files]
        gen_dissemination_times_boxplot(block_sizes, dissemination_times, argv[2])
        

if __name__ == "__main__":
    main()
