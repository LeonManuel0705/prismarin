package net.prismarin.example;

record Stats(String name, int wins, int games) {

    static Stats fresh(String name) {
        return new Stats(name, 0, 0);
    }

    Stats won() {
        return new Stats(name, wins + 1, games + 1);
    }
}
