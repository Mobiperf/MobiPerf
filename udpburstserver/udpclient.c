/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    printf("usage:  %s <IP address> <port>\n", argv[0]);
    return -1;
  }

  lport = strtol(argv[2], NULL, 10);

  if (lport < 1 || lport > 65535) {
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
