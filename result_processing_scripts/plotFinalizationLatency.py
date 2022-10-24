from sys import argv
import pandas as pd
import matplotlib.pyplot as plt
import statistics


def gen_finalization_latency_boxplot(num_chains, finalization_times, output_pathname):
    '''
    Generate a graph presenting the finalization time of blocks by varying the number of chains employed.
    '''
    finalization_times_sec = [[time / 1000 for time in times] for times in finalization_times]
    median = [statistics.median(times) for times in finalization_times_sec]
    axis_plt = list(range(1, len(num_chains) + 1))
    plt.boxplot(finalization_times_sec, labels=num_chains, showfliers=False)
    plt.plot(axis_plt, median, 'o--', markersize=3, linewidth=1)
    plt.grid(color='grey', axis='y', linestyle='-', linewidth=0.25, alpha=0.5)
    plt.xlabel('Number of Parallel Chains')
    plt.ylabel('Block Finalization Time (s)')
    plt.savefig(output_pathname)


def get_finalized_latency_from_file(file):
    return list(pd.read_csv(file, index_col='BlockID')['Elapsed Finalization Latency'])


def main():
    if len(argv) < 3:
        print('./plotFinalizationLatency <Finalization Latency Pointer File> <Graph Output Pathname>')
        print('Finalization Latency Pointer File: File containing the pathname of the files containing the average finalization time of blocks, indexed by the number of parallel chains in use.')
        print('Graph Output Pathname: Output pathname for the dissemination time graph.')
    else:
        finalized_pointer = pd.read_csv(argv[1])
        num_chains = list(finalized_pointer['Num Chains'])
        finalization_files = list(finalized_pointer['Pointed File'])
        finalization_times = [get_finalized_latency_from_file(file) for file in finalization_files]
        gen_finalization_latency_boxplot(num_chains, finalization_times, argv[2])
        

if __name__ == "__main__":
    main()
