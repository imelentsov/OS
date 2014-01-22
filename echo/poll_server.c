#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/poll.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <errno.h>
#include <stdbool.h>

#define SERVER_PORT  1337
#define BUF_SIZE 1024
#define CONNECTIONS_ALLOWED 32
int const CONNECTION_TIMEOUT = 2 * 60 * 1000; // in ms

int err_exit(char* err, int socketDescriptor) {
	perror(err);
	close(socketDescriptor);
	exit(-1);
}

int main(int argc, char *argv[]) {
	// Not buffering output
	setbuf(stdout, NULL);

	int len, rc;
	int socketDescriptor = -1, new_sd = -1;
	bool end_server = false, compress_array = false, close_conn;
	char buffer[BUF_SIZE];
	struct sockaddr_in addr;
	struct pollfd fds[CONNECTIONS_ALLOWED];
	int nfds = 1, current_size = 0, i, j;

	// Create an AF_INET stream socket to receive incoming connections on
	socketDescriptor = socket(AF_INET, SOCK_STREAM, 0);
	if (socketDescriptor < 0) {
		perror("socket() failed");
		exit(-1);
	}

	// Allow socket descriptor to be reusable
	int optVal = 1;
	rc = setsockopt(socketDescriptor, SOL_SOCKET, SO_REUSEADDR, &optVal, sizeof(optVal));
	if (rc < 0) {
		err_exit("setsockopt() failed", socketDescriptor);
	}

	// Set socket to be nonblocking
	rc = ioctl(socketDescriptor, FIONBIO, &optVal);
	if (rc < 0) {
		err_exit("ioctl() failed", socketDescriptor);
	}

	// Bind the socket
	memset(&addr, 0, sizeof(addr));
	addr.sin_family = AF_INET;
	addr.sin_addr.s_addr = htonl(INADDR_ANY);
	addr.sin_port = htons(SERVER_PORT);
	rc = bind(socketDescriptor, (struct sockaddr *) &addr, sizeof(addr));
	if (rc < 0) {
		err_exit("bind() failed", socketDescriptor);
	}

	// Set the listen back log
	rc = listen(socketDescriptor, CONNECTIONS_ALLOWED);
	if (rc < 0) {
		err_exit("listen() failed", socketDescriptor);
	}

	/*************************************************************/
	/* Initialize the pollfd structure                           */
	/*************************************************************/
	memset(fds, 0, sizeof(fds));

	/*************************************************************/
	/* Set up the initial listening socket                        */
	/*************************************************************/
	fds[0].fd = socketDescriptor;
	fds[0].events = POLLIN;

	/*************************************************************/
	/* Loop waiting for incoming connects or for incoming data   */
	/* on any of the connected sockets.                          */
	/*************************************************************/
	printf("Server started.\n");
	while (!end_server){
		printf("Waiting on poll()...\n");
		rc = poll(fds, nfds, CONNECTION_TIMEOUT);

		if (rc < 0) {
			perror("  poll() failed");
			break;
		}

		if (rc == 0) {
			printf("  poll() timed out.  End program.\n");
			break;
		}

		current_size = nfds;
		for (i = 0; i < current_size; i++) {
			if (fds[i].revents == 0)
				continue;

			if (fds[i].revents != POLLIN) {
				printf("  Error! revents = %d\n", fds[i].revents);
				end_server = true;
				break;

			}
			if (fds[i].fd == socketDescriptor) {
				printf("  Listening socket is readable\n");
				new_sd = accept(socketDescriptor, NULL, NULL);
				if (new_sd < 0) {
					perror("  accept() failed");
					end_server = true;
				} else {
					printf("  New incoming connection - %d\n", new_sd);
					fds[nfds].fd = new_sd;
					fds[nfds].events = POLLIN;
					nfds++;
				}
			} else {
				printf("  Descriptor %d is readable\n", fds[i].fd);
				close_conn = false;

				rc = recv(fds[i].fd, buffer, sizeof(buffer), 0);
				if (rc < 0) {
					perror("  recv() failed");
					close_conn = true;
				} else if (rc == 0) {
					printf("  Connection closed\n");
					close_conn = true;
				} else {
					len = rc;
					printf("  %d bytes received: ", len);
					char* readed = malloc(sizeof(char) * (len + 1));
					memcpy(&*readed, &buffer, len);
					readed[len] = 0;
					printf(readed);
					printf("\n");
					rc = send(fds[i].fd, buffer, len, 0);
					if (rc < 0) {
						perror("  send() failed");
						close_conn = true;
					}
				}

				if (close_conn) {
					close(fds[i].fd);
					fds[i].fd = -1;
					compress_array = true;
				}

			}
		}

		if (compress_array) {
			compress_array = false;
			for (i = 0; i < nfds; i++) {
				if (fds[i].fd == -1) {
					for (j = i; j < nfds; j++) {
						fds[j].fd = fds[j + 1].fd;
					}
					i--;
					nfds--;
				}
			}
		}

	}
	for (i = 0; i < nfds; i++) {
		if (fds[i].fd >= 0)
			close(fds[i].fd);
	}

}
