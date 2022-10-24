import pandas as pd
from sys import argv
import matplotlib.pyplot as plt
import statistics


def gen_throughput_latency_graph_boxplot(finalization_latencies_csv, output_pathname):
    '''
    Generate a graph presenting the relation between the throughput and latency as the number of chains employed increases.
    '''
    finalization_times = [list(csv['Average Block Finalization Time']) for csv in finalization_latencies_csv]
    throughputs = compute_throughput(finalization_times)
    finalization_latencies = [list(csv['Elapsed Finalization Latency']) for csv in finalization_latencies_csv]
    medians = [statistics.median(times) for times in finalization_latencies]
    plt.boxplot(finalization_latencies, labels=throughputs, showfliers=False)
    plt.plot(throughputs, medians, 'o--', markersize=3, linewidth=1)
    plt.grid(color='grey', axis='y', linestyle='-', linewidth=0.25, alpha=0.5)
    plt.xlabel('Throughput (blocks/s)')
    plt.ylabel('Latency (s)')
    plt.savefig(output_pathname)


def compute_throughput(finalization_times):
    '''
    Compute the throughput of the execution traces
    '''
    min_times = [min(times) for times in finalization_times]
    max_times = [max(times) for times in finalization_times]
    elapsed = [max_val - min_val for max_val, min_val in zip(max_times, min_times)]
    num_blocks = [len(blocks) for blocks in finalization_times]
    return [bl * 1000 / el for bl, el in zip(num_blocks, elapsed)]  


def main():
    if len(argv) < 3:
        print('./plotFinalizationLatency <Finalization Latency Pointer File> <Graph Output Pathname>')
        print('Finalization Latency Pointer File: File containing the pathname of the files containing the average finalization time of blocks, indexed by the number of parallel chains in use.')
        print('Graph Output Pathname: Output pathname for the dissemination time graph.')
    else:
        finalized_pointer = pd.read_csv(argv[1])
        finalization_latencies_csv = [pd.read_csv(file, index_col='BlockID') for file in list(finalized_pointer['Pointed File'])]
        gen_throughput_latency_graph_boxplot(finalization_latencies_csv, argv[2])
        

if __name__ == "__main__":
    main()
    