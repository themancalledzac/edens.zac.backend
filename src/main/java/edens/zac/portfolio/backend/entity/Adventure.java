//package edens.zac.portfolio.backend.entity;
//
//import jakarta.persistence.Entity;
//import jakarta.persistence.GeneratedValue;
//import jakarta.persistence.GenerationType;
//import jakarta.persistence.Id;
//import jakarta.persistence.ManyToMany;
//
//import java.util.HashSet;
//import java.util.Set;
//
//@Entity
//public class Adventure {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//    private String name;
//
//    @ManyToMany(mappedBy = "adventures")
//    private Set<Image> images = new HashSet<>();
//}
