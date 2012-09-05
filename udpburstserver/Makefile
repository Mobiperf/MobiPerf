CC=gcc
CFLAGS=-Wall
DEPS = hellomake.h

%.o: %.c $(DEPS)
	$(CC) -c -o $@ $< $(CFLAGS)

all: udpburstserver udpclient

udpburstserver: udpburstserver.o
	gcc -o udpburstserver udpburstserver.o

udpclient: udpclient.o
	gcc -o udpclient udpclient.o

clean:
	rm -f *.o *~ core udpburstserver udpclient