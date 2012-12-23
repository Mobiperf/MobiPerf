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

/* UDPBurst Server */

#include <sys/socket.h>
#include <netinet/in.h>
#include <stdio.h>
#include <strings.h>
#include <string.h>
#include <sys/time.h>
#include <time.h>
#include <assert.h>
#include <unistd.h>
#include <sys/select.h>
#include <arpa/inet.h>
#include <stdlib.h>

#define CLIARRAYSIZE 256
#define BUFSIZE 1500
#define STRBUFSIZE 40

#define TIMEOUT 3

#define PKT_ERROR 1
#define PKT_RESPONSE 2
#define PKT_DATA 3
#define PKT_REQUEST 4

struct clientrec {
  unsigned char full;        // used/unused record
  unsigned long addr;        // client address
  unsigned short port;       // client port
  unsigned int seq;          // burst sequence
  unsigned int pktrecvd;     // how many packets received so far
  unsigned int burstsize;    // size of the burst
  unsigned int pktsize;      // size of packet to be sent
  struct timeval lastrecvd;  // last time a packet was received
};

struct packet {
  unsigned int ptype;
  unsigned int burstsize;
  unsigned int pktnum;
  unsigned int pktsize;
  unsigned int seq;
};

/**
 * Prints a log message, including a timestamp. Second argument can be NULL
 *
 */
void logmsg(char *msg1, char *msg2) {
  time_t timer;
  char buffer[25];
  struct tm* tm_info;

  assert(msg1);
  time(&timer);
  tm_info = localtime(&timer);
  strftime(buffer, 25, "%Y:%m:%d:%H:%M:%S", tm_info);

  if (msg2) {
    printf("%s %s %s\n", buffer, msg1, msg2);
  } else {
    printf("%s %s\n", buffer, msg1);
  }
}

/**
 *
 * Build a packet of the requested type addressed to clientp and
 *  sends it out of sockfd.
 *
 * Returns
 *  0, on success
 * -1, on failure
 *
 */
int sendresponse(int sockfd, struct clientrec *clientp, unsigned int type) {
  struct sockaddr_in cliaddr;
  struct packet resppacket;
  char buf[STRBUFSIZE];
  int resp;
  char *buffer;

  assert(clientp);

  cliaddr.sin_family = AF_INET;
  cliaddr.sin_addr.s_addr = htonl(clientp->addr);
  cliaddr.sin_port = htons(clientp->port);

  resppacket.burstsize = htonl(clientp->burstsize);
  resppacket.pktnum = htonl(clientp->pktrecvd);
  resppacket.pktsize = htonl(clientp->pktsize);
  resppacket.seq = htonl(clientp->seq);

  switch (type) {
    case PKT_ERROR:
      resppacket.ptype = htonl(PKT_ERROR);
      break;
    case PKT_RESPONSE:
      resppacket.ptype = htonl(PKT_RESPONSE);
      break;
    case PKT_DATA:
      resppacket.ptype = htonl(PKT_DATA);
      break;
    default:
      return -1;
  }

  snprintf(buf, sizeof(buf), "%s b:%d p:%d s:%d", inet_ntoa(cliaddr.sin_addr),
           clientp->burstsize, clientp->pktrecvd, clientp->pktsize);
  buffer = (char *) malloc((size_t) clientp->pktsize);

  if (!buffer) {
    logmsg("Error allocating buffer for response to", buf);
    return -1;
  }

  memcpy(buffer, (void *) &resppacket, sizeof(resppacket));

  if ((resp = sendto(sockfd, buffer, clientp->pktsize, 0,
                     (struct sockaddr *) &cliaddr, sizeof(cliaddr))) == -1) {
    logmsg("Error sending response to", buf);
    return -1;
  }

  snprintf(buf, sizeof(buf), "%s b:%d p:%d s:%d", inet_ntoa(cliaddr.sin_addr),
           clientp->burstsize, clientp->pktrecvd, resp);
  logmsg("Sent response to", buf);
  return 0;
}

/**
 *
 * Cleans up record pointed by clientp
 *
 */
void cleanrecord(struct clientrec *clientp) {
  assert(clientp);

  clientp->full = 0;
  clientp->addr = 0;
  clientp->port = 0;
  clientp->seq = 0;
  clientp->pktrecvd = 0;
  clientp->burstsize = 0;
  clientp->pktsize = 0;
  clientp->lastrecvd.tv_sec = 0;
  clientp->lastrecvd.tv_usec = 0;
}  // cleanrecord()

/**
 *
 * Goes over the table of records and cleans up those that have not received a
 * message in the last TIMEOUT seconds. For each of those records, it calls
 * sendresponse() to transmit a response to the corresponding client.
 *
 * Returns,
 *  0, on success
 * -1, on failure
 *
 */
int removeoldrecords(struct clientrec *clientp, int sockfd) {
  int i;
  struct clientrec client;
  struct timeval curtime;

  assert(clientp);

  if (gettimeofday(&curtime, NULL) < 0) {
    return -1;
  }

  for (i = 0; i < CLIARRAYSIZE; i++) {
    client = clientp[i];
    if (client.full && curtime.tv_sec - client.lastrecvd.tv_sec > TIMEOUT) {
      sendresponse(sockfd, clientp + i, PKT_RESPONSE);
      cleanrecord(clientp + i);
    }
  }
  return 0;
}  // removeoldrecords()

/**
 *
 */
int processpacket(int sockfd, struct clientrec *clientp, char *msgp,
                  struct sockaddr_in cliaddr) {
  unsigned char index;
  struct clientrec *cp;
  struct timeval tim;
  struct packet *clipacketp;
  char buf[STRBUFSIZE];
  int ptype, i, burstsize;

  assert(msgp);
  assert(clientp);

  index = ntohl(cliaddr.sin_addr.s_addr) & 0x000000FF;
  clipacketp = (struct packet *) msgp;
  ptype = ntohl(clipacketp->ptype);

  if (ptype != PKT_DATA && ptype != PKT_REQUEST) {
    cp = (struct clientrec *) malloc(sizeof(struct clientrec));
    assert(cp);
    cp->addr = ntohl(cliaddr.sin_addr.s_addr);
    cp->port = ntohs(cliaddr.sin_port);
    cp->burstsize = 0;
    cp->pktrecvd = 0;
    cp->seq = 0;
    cp->pktsize = sizeof(struct packet);
    sendresponse(sockfd, cp, PKT_ERROR);
    logmsg("Malformed packet", NULL);
    free(cp);
    return -1;
  }

  if (ptype == PKT_REQUEST) {
    cp = (struct clientrec *) malloc(sizeof(struct clientrec));
    assert(cp);
    burstsize = ntohl(clipacketp->burstsize);

    cp->addr = ntohl(cliaddr.sin_addr.s_addr);
    cp->port = ntohs(cliaddr.sin_port);
    cp->pktsize = ntohl(clipacketp->pktsize);
    cp->burstsize = burstsize;
    cp->seq = 0;

    if (cp->pktsize < sizeof(struct packet)) {
      return -1;
    }

    for (i = 0; i < burstsize; i++) {
      cp->pktrecvd = i;
      sendresponse(sockfd, cp, PKT_DATA);
    }
    free(cp);
    return 0;
  }

  snprintf(buf, sizeof(buf), "s:%d b:%d p:%d", ntohl(clipacketp->seq),
           ntohl(clipacketp->burstsize), ntohl(clipacketp->pktnum));
  logmsg("Data packet", buf);

  cp = clientp + index;

  if (cp->full) {
    // record already exists
    if (cp->addr == ntohl(cliaddr.sin_addr.s_addr) &&
        cp->port == ntohs(cliaddr.sin_port) &&
        cp->seq == ntohl(clipacketp->seq)) {
      cp->pktrecvd++;
    } else {
      // record is used by a different client or
      // client sent a different sequence number
      sendresponse(sockfd, cp, PKT_ERROR);
      return -1;
    }
  } else {
    // empty record
    cp->full = 1;
    cp->addr = ntohl(cliaddr.sin_addr.s_addr);
    cp->port = ntohs(cliaddr.sin_port);
    cp->burstsize = ntohl(clipacketp->burstsize);
    cp->pktsize = ntohl(clipacketp->pktsize);
    cp->pktrecvd = 1;
    cp->seq = ntohl(clipacketp->seq);
  }

  if (gettimeofday(&tim, NULL) < 0)  {
    return -1;
  }
  cp->lastrecvd.tv_sec = tim.tv_sec;
  cp->lastrecvd.tv_usec = tim.tv_usec;

  if (cp->pktrecvd == cp->burstsize) {
    // send a response and clean the record
    sendresponse(sockfd, cp, PKT_RESPONSE);
    cleanrecord(cp);
  }

  return 0;
}  // processpacket()

int main(int argc, char**argv) {
  int sockfd, i, rc;
  struct sockaddr_in servaddr, cliaddr;
  socklen_t len;
  char mesg[BUFSIZE];
  struct clientrec clients[CLIARRAYSIZE];
  fd_set fdset, workset;
  struct timeval timeout;
  long lport;

  if (argc != 2) {
    printf("Usage %s portnumber\n", argv[0]);
    return -1;
  }

  lport = strtol(argv[1], NULL, 10);

  if (lport < 1 || lport > 65535) {
    printf("Invalid port %ld\n", lport);
    return -1;
  }

  // Initialize the client array

  for (i = 0; i < CLIARRAYSIZE; i++) {
    cleanrecord(clients + i);
  }

  // Open the socket and bind to the port
  sockfd = socket(AF_INET, SOCK_DGRAM, 0);
  if (sockfd == -1) {
    perror("Failed opening socket");
    return -1;
  }

  bzero(&servaddr, sizeof(servaddr));
  servaddr.sin_family = AF_INET;
  servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
  servaddr.sin_port = htons(lport);
  if (bind(sockfd, (struct sockaddr *) &servaddr, sizeof(servaddr))) {
    perror("Error binding socket");
    close(sockfd);
    return -1;
  }

  FD_ZERO(&fdset);
  FD_SET(sockfd, &fdset);
  timeout.tv_sec = TIMEOUT;
  timeout.tv_usec = 0;

  for (;;) {
    memcpy(&workset, &fdset, sizeof(fdset));
    rc = select(sockfd+1, &workset, NULL, NULL, &timeout);

    if (rc < 0) {
      perror("select() failed");
      break;
    }

    if (rc == 0) {
      // Timeout occured
      // logmsg("Cleaning up records", NULL);
      removeoldrecords(clients, sockfd);
    }

    if (FD_ISSET(sockfd, &workset)) {
      len = sizeof(cliaddr);
      recvfrom(sockfd, mesg, BUFSIZE, 0, (struct sockaddr *) &cliaddr, &len);

      logmsg("received message from", inet_ntoa(cliaddr.sin_addr));

      if (processpacket(sockfd, clients, mesg, cliaddr)) {
        // log error
        logmsg("Error processing message", NULL);
      }
    }
  }
  return 0;
}
