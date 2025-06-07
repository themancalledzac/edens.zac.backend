package edens.zac.portfolio.backend.services;

import org.springframework.stereotype.Component;

@Component
public class PaginationUtil {
    
    public static boolean hasMorePages(Integer currentPage, Integer totalPages) {
        return currentPage != null && totalPages != null && currentPage < totalPages;
    }
    
    public static int getStartIndex(Integer currentPage, Integer blocksPerPage) {
        if (currentPage == null || blocksPerPage == null) return 1;
        return ((currentPage - 1) * blocksPerPage) + 1;
    }
    
    public static int getEndIndex(Integer currentPage, Integer blocksPerPage, Integer totalBlocks) {
        if (currentPage == null || blocksPerPage == null || totalBlocks == null) {
            return 0;
        }
        int calculated = currentPage * blocksPerPage;
        return Math.min(calculated, totalBlocks);
    }
}