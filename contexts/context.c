#include "stdio.h"
#include "signal.h"
#include "unistd.h"
#include "ucontext.h"
#include "stdbool.h"
#include "stdlib.h"

int const F1_TIME = 2, F2_TIME = 4;

ucontext_t firstUC, secondUC;
bool turn = false;

void func1() {
	while (1) {
		printf("1");
	}
}

void func2() {
	while (1) {
		printf("2");
	}
}

void scheduler() {
	if (turn = !turn) {
		alarm(F2_TIME);
		swapcontext(&firstUC, &secondUC);//switch to secondUC
	} else {
		alarm(F1_TIME);
		swapcontext(&secondUC, &firstUC);
	}
}

void main() {
	getcontext(&firstUC);
	firstUC.uc_stack.ss_size = SIGSTKSZ;
	firstUC.uc_stack.ss_sp = malloc(firstUC.uc_stack.ss_size);
	makecontext(&firstUC, func1, 0);

	getcontext(&secondUC);
	secondUC.uc_stack.ss_size = SIGSTKSZ;
	secondUC.uc_stack.ss_sp = malloc(secondUC.uc_stack.ss_size);
	makecontext(&secondUC, func2, 0);

	signal(SIGALRM, scheduler);
	alarm(F1_TIME);
	setcontext(&firstUC);
}