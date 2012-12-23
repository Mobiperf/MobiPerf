CC=gcc
CFLAGS=-Wall -m32 -O3
LDFLAGS=-m32
DEPS = hellomake.h

%.o: %.c $(DEPS)
	$(CC) -c -o $@ $< $(CFLAGS)

all: udpburstserver udpclient

udpburstserver: udpburstserver.o
	gcc $(LDFLAGS) -o udpburstserver udpburstserver.o

udpclient: udpclient.o
	gcc $(LDFLAGS) -o udpclient udpclient.o

clean:
	rm -f *.o *~ core udpburstserver udpclient
