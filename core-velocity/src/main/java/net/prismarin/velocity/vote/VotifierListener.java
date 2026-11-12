package net.prismarin.velocity.vote;

import com.velocitypowered.api.event.Subscribe;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.velocity.event.VotifierEvent;
import org.slf4j.Logger;

public final class VotifierListener {

    public static final String PLUGIN_ID = "nuvotifier";

    private final ProxyVoteService votes;
    private final Logger logger;

    public VotifierListener(ProxyVoteService votes, Logger logger) {
        this.votes = votes;
        this.logger = logger;
    }

    @Subscribe
    public void onVote(VotifierEvent event) {
        Vote vote = event.getVote();
        if (vote == null) {
            return;
        }
        String name = vote.getUsername();
        String service = vote.getServiceName();
        votes.receive(name, service, vote.getAddress()).exceptionally(error -> {
            logger.warn("Could not count the vote of {} from {}: {}", name, service, error.getMessage());
            return null;
        });
    }
}
