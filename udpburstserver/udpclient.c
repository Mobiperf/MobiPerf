// Copyright 2012 Google Inc. All Rights Reserved.


/* Test UDP client */

#include <stdlib.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <stdio.h>
#include <strings.h>
#include <arpa/inet.h>

#define PKT_ERROR 1
#define PKT_RESPONSE 2
#define PKT_DATA 3
#define PKT_REQUEST 4

struct packet {
  unsigned int ptype;
  unsigned int burstsize;
  unsigned int pktnum;
  unsigned int pktsize;
  unsigned int seq;
};


int main(int argc, char**argv) {
  int sockfd;
  struct sockaddr_in servaddr;
  struct packet pkt;
  long lport;

  if (argc != 3) {
    printf("usage:  %s <IP address> port\n", argv[0]);
    return -1;
  }

  lport = strtol(argv[2], NULL, 10);

  if (lport < 0 || lport > 65535) {
    printf("Invalid port %ld\n", lport);
    return -1;
  }

  sockfd = socket(AF_INET, SOCK_DGRAM, 0);

  bzero(&servaddr, sizeof(servaddr));
  servaddr.sin_family = AF_INET;
  servaddr.sin_addr.s_addr = inet_addr(argv[1]);
  servaddr.sin_port = htons(lport);

  pkt.ptype = htonl(PKT_REQUEST);
  pkt.burstsize = htonl(4);
  pkt.pktnum = htonl(1);
  pkt.pktsize = htonl(500);
  pkt.seq = htonl(1);

  sendto(sockfd, &pkt, sizeof(struct packet), 0,
         (struct sockaddr *)&servaddr, sizeof(servaddr));

  return 0;
}
