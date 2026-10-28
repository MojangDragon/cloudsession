package hu.blockeater;

import java.util.Set;
import java.util.TreeSet;

/** Egy játékos állása. */
public final class PlayerData {
    public long eaten;        // összes megevett blokk (ez oldja fel a tárgyakat)
    public long balance;      // költhető blokkok (egy blokk = 1)
    public final Set<Integer> owned = new TreeSet<>();
    public boolean hud = true;
    public boolean dirty;
}
