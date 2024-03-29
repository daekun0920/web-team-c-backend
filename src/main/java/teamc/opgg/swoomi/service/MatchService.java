package teamc.opgg.swoomi.service;

import com.merakianalytics.orianna.Orianna;
import com.merakianalytics.orianna.types.common.Region;
import com.merakianalytics.orianna.types.core.spectator.CurrentMatch;
import com.merakianalytics.orianna.types.core.spectator.Player;
import com.merakianalytics.orianna.types.core.summoner.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import teamc.opgg.swoomi.advice.exception.CSummonerNotFoundException;
import teamc.opgg.swoomi.advice.exception.CSummonerNotInGameException;
import teamc.opgg.swoomi.dto.*;
import teamc.opgg.swoomi.entity.ChampionItem;
import teamc.opgg.swoomi.entity.MatchTeamCodeSummoner;
import teamc.opgg.swoomi.repository.ChampionItemRepository;
import teamc.opgg.swoomi.repository.MatchTeamCodeSummonerRepository;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {
    private final ChampionItemRepository championItemRepository;
    private final MatchTeamCodeSummonerRepository matchTeamCodeSummonerRepository;
    private final OriannaService oriannaService;

    @Value("${riot.api.key}")
    private String RIOT_API_KEY;

    @Transactional(readOnly = true)
    public MatchDto getMatchStatus(String encryptedSummonerName) {
        MatchDto dto = new MatchDto();
        String riotUrl = "https://kr.api.riotgames.com/lol/spectator/v4/active-games/by-summoner/";
        riotUrl = riotUrl + encryptedSummonerName + "?api_key=" + RIOT_API_KEY;

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response;

        try {
            response = restTemplate.getForEntity(riotUrl, String.class);
            log.info("Response Code : " + response.getStatusCode());
            dto.setMatchStatus(response.getStatusCode() == HttpStatus.OK);
        } catch (Exception e) {
            dto.setMatchStatus(false);
        }
        log.info("현재 매치 상태 : " + (dto.isMatchStatus() ? "시작 함" : "시작 안함"));

        return dto;
    }

    @Transactional
    public MatchDto getMatchStatusByMatchTeamCode(String matchTeamCode) {
        String summonerName = matchTeamCodeSummonerRepository
                .findFirstByMatchTeamCode(matchTeamCode)
                .orElseThrow(CSummonerNotInGameException::new)
                .getSummonerName();
        String summonerId = oriannaService.summonerFindByNameAndSave(summonerName).getSummonerId();
        return getMatchStatus(summonerId);
    }

    @Synchronized
    @Transactional
    public MatchStatusDto getMatchTeamCode(String summonerName) {
        log.info("["+summonerName+"] CASE : GET MATCH_TEAM_CODE");
        boolean isMyTeam = false;
        long myTeam = 100;
        Summoner summoner;
        MatchStatusDto matchStatusDto;

        summoner = Orianna.summonerNamed(summonerName).withRegion(Region.KOREA).get();
        if (!summoner.exists()) {
            throw new CSummonerNotFoundException();
        }

        CurrentMatch currentMatch = Orianna.currentMatchForSummoner(summoner).get();
        if (currentMatch.getId() != 0) {
            matchStatusDto = MatchStatusDto.builder()
                    .isStarted(false)
                    .matchTeamCode("")
                    .build();
            for (int i = 0; i < 5; i++) {
                Player player = currentMatch.getParticipants().get(i);
                if (player.getSummoner().getName().equals(summonerName)) {
                    isMyTeam = true;
                    break;
                }
            }
            if (!isMyTeam) myTeam = 200;
            String matchTeamCode = String.valueOf(currentMatch.getId() * 1000 + myTeam);
            matchStatusDto.setIsStarted(true);
            matchStatusDto.setMatchTeamCode(matchTeamCode);

            if (matchTeamCodeSummonerRepository.findBySummonerName(summonerName).isPresent()) {
                matchTeamCodeSummonerRepository.findBySummonerName(summonerName).get()
                        .setMatchTeamCode(matchTeamCode);
                log.info("기존값 저장");
            } else {
                MatchTeamCodeSummoner matchTeamCodeSummoner = MatchTeamCodeSummoner.builder()
                        .matchTeamCode(matchTeamCode)
                        .summonerName(summonerName)
                        .build();
                matchTeamCodeSummonerRepository.save(matchTeamCodeSummoner);
                log.info("새로 저장");
            }
            return matchStatusDto;
        }
        else {
            throw new CSummonerNotInGameException();
        }
    }

    public String getMyMatchTeamCodeByEnemy(String matchTeamCode) {
        StringBuilder myCode = new StringBuilder(matchTeamCode);
        if (Integer.parseInt(myCode.substring(myCode.length() - 3)) == 100) {
            myCode.replace(myCode.length() - 3, myCode.length(), "200");
        } else {
            myCode.replace(myCode.length() - 3, myCode.length(), "100");
        }
        return myCode.toString();
    }

    @Transactional(readOnly = true)
    public List<ItemDto> getFrequentItems(String championName, String position) {
        Optional<List<ChampionItem>> frequentItems =
                championItemRepository.findAllByChampionNameAndPosition(championName, position);
        return frequentItems.map(championItems -> championItems
                .stream()
                .map(
                        (i) -> ItemDto.builder()
                                .name(i.getItemName())
                                .englishName(i.getEnglishName())
                                .src(i.getSrc())
                                .skillAccel(i.getSkillAccel())
                                .build()
                )
                .collect(Collectors.toList())).orElseGet(ArrayList::new);
    }

    public String getEncryptedSummonerId(String summonerName) {
        return Orianna
                .summonerNamed(summonerName)
                .withRegion(Region.KOREA)
                .get()
                .getId();
    }
}
