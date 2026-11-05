package net.jforum.user;

import java.util.List;

import net.jforum.domain.Ranking;
import net.jforum.domain.User;
import net.jforum.repository.RankingRepository;

import org.springframework.stereotype.Service;

/** Legacy RankingRepository.getRankTitle: special rank if assigned, otherwise the highest rank by post count. */
@Service("rank")
public class RankingService {

    private final RankingRepository rankings;

    public RankingService(RankingRepository rankings) {
        this.rankings = rankings;
    }

    public String getRankTitle(User user) {
        return user == null ? "" : getRankTitle(user.getTotalPosts(), user.getRankId());
    }

    public String getRankTitle(int totalPosts, Integer rankId) {
        List<Ranking> all = rankings.findAllByOrderByMinAsc();
        if (rankId != null && rankId > 0) {
            for (Ranking r : all) {
                if (r.isSpecial() && r.getId().equals(rankId)) {
                    return r.getTitle();
                }
            }
        }
        String title = "";
        for (Ranking r : all) {
            if (!r.isSpecial() && totalPosts >= r.getMin()) {
                title = r.getTitle();
            }
        }
        return title;
    }

    public List<Ranking> specialRanks() {
        return rankings.findAllByOrderByMinAsc().stream().filter(Ranking::isSpecial).toList();
    }
}
