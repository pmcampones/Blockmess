import pandas as pd
from sys import argv
import matplotlib.pyplot as plt

def gen_throughput_boxplot(num_chains, finalization_times, output_pathname):
    '''
    Generate a graph presenting the block throughput by varying the block size.
    '''
    throughput = compute_throughput(finalization_times)
    plt.plot(num_chains, throughput, 'o--', markersize=3, linewidth=1)
    plt.grid(color='grey', axis='y', linestyle='-', linewidth=0.25, alpha=0.5)
    plt.xlabel('Number of Parallel Chains')
    plt.ylabel('Throughput (blocks/s)')
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

    
def get_finalized_times_from_file(file):
    return list(pd.read_csv(file, index_col='BlockID')['Average Block Finalization Time'])


def main():
    if len(argv) < 3:
        print('./plotFinalizationLatency <Finalization Latency Pointer File> <Graph Output Pathname>')
        print('Finalization Latency Pointer File: File containing the pathname of the files containing the average finalization time of blocks, indexed by the number of parallel chains in use.')
        print('Graph Output Pathname: Output pathname for the dissemination time graph.')
    else:
        finalized_pointer = pd.read_csv(argv[1])
        num_chains = list(finalized_pointer['Num Chains'])
        finalization_files = list(finalized_pointer['Pointed File'])
        finalization_times = [get_finalized_times_from_file(file) for file in finalization_files]
        gen_throughput_boxplot(num_chains, finalization_times, argv[2])
        

if __name__ == "__main__":
    main()
