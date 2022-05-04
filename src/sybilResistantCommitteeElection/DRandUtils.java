package sybilResistantCommitteeElection;

import com.google.gson.Gson;
import coresearch.cvurl.io.model.Response;
import coresearch.cvurl.io.request.CVurl;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DRandUtils {

    private static final String DRAND_URL = "https://api.drand.sh/public/%s";

    private final String drandUrl;

    private final Map<Integer, DRandRound> seenDRandRounds = new HashMap<>();

    public DRandUtils(Properties props) {
        this.drandUrl = props.getProperty("drandurl", DRAND_URL);
    }

    public DRandRound getDRandRound(int round) throws Exception {
        DRandRound dRandRound = seenDRandRounds.get(round);
        if (dRandRound == null) {
            String fullURL = String.format(drandUrl, round);
            dRandRound = getDRandRoundFromURL(fullURL);
            seenDRandRounds.put(round, dRandRound);
        }
        return dRandRound;
    }

    public DRandRound getLatestDRandRound() throws Exception {
        String fullURL = String.format(drandUrl, "latest");
        DRandRound dRandRound = getDRandRoundFromURL(fullURL);
        seenDRandRounds.put(dRandRound.getRound(), dRandRound);
        return dRandRound;
    }

    private static DRandRound getDRandRoundFromURL(String drandUrl) throws Exception {
        CVurl cvurl = new CVurl();
        Gson gson = new Gson();
        Response<String> response = cvurl.get(drandUrl)
                .asString()
                .orElseThrow(Exception::new);
        return gson.fromJson(response.getBody(), DRandRound.class);
    }
}
