import os
from sys import argv

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization


def gen_key_pair():
    sk = ec.generate_private_key(ec.SECP256R1())
    pk = sk.public_key()
    pem_sk = sk.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption()
    )
    pem_pk = pk.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    return pem_sk, pem_pk


def record_key(key, output_pathname):
    full_pathname = os.path.join(os.path.dirname(__file__), output_pathname)
    with open(full_pathname, 'wb') as fout:
        fout.write(key)


def write_key_pointer(num_keys):
    pointer_pathname = os.path.join(os.path.dirname(__file__), 'key_pointer.txt')
    keys_pathname_stub = os.path.join(os.path.dirname(__file__), 'pk/pk{}.pem\n')
    with open(pointer_pathname, 'w') as fout:
        [fout.write(keys_pathname_stub.format(i)) for i in range(num_keys)]


if __name__ == "__main__":
    if len(argv) < 2:
        print('Usage: python3 gen_key_pair.py <Num Keys>')
        exit(1)
    num_keys = int(argv[1])
    sks, pks = zip(*[gen_key_pair() for _ in range(num_keys)])
    [record_key(sk, f'sk/sk{i}.pem') for i, sk in enumerate(sks)]
    [record_key(pk, f'pk/pk{i}.pem') for i, pk in enumerate(pks)]
    write_key_pointer(num_keys)
