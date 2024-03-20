package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.model.AdventureModalDTO;
import edens.zac.portfolio.backend.model.AdventureModel;
import edens.zac.portfolio.backend.services.AdventureService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/adventure")
public class AdventureController {

    private final AdventureService adventureService;

    @PostMapping("/createAdventure")
    public String createAdventure(@RequestBody AdventureModalDTO adventure) {

        return adventureService.createAdventure(adventure);
    }

    @RequestMapping(value = "/mainPageAdventureList")
    public List<AdventureModel> getMainPageAdventureList() {

        return adventureService.getMainPageAdventureList();
    }
}
