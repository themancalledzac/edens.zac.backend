package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.AdminHomeTileRepository;
import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for admin home dashboard operations. */
@Service
@RequiredArgsConstructor
public class AdminHomeService {

  private final AdminHomeTileRepository adminHomeTileRepository;

  @Transactional(readOnly = true)
  public List<Records.AdminHomeTileResponse> getTiles() {
    return adminHomeTileRepository.findAllWithCover();
  }
}
