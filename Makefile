.PHONY: clean compile test jar uberjar repl

all: clean jar install

clean:
	clj -T:build clean

jar: clean
	clj -T:build jar

install: jar
	clj -T:build install
