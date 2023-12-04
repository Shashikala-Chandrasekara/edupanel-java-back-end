package lk.ijse.dep11.edupanel.api;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import lk.ijse.dep11.edupanel.to.request.LecturerRequestTO;
import lk.ijse.dep11.edupanel.to.response.LecturerRespTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/lecturers")
@CrossOrigin
public class LecturerHttpController {

    @Autowired
    private DataSource pool;

    @Autowired
    private Bucket bucket;


    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = "multipart/form-data", produces = "application/json")
    public LecturerRespTO createNewLecturer(@ModelAttribute @Valid LecturerRequestTO lecturer){
        try (Connection connection = pool.getConnection()) {

            connection.setAutoCommit(false);
            try{
                PreparedStatement stmInsert = connection.prepareStatement("INSERT INTO lecturer (name, designation, qualifications, linkedin) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                stmInsert.setString(1, lecturer.getName());
                stmInsert.setString(2, lecturer.getDesignation());
                stmInsert.setString(3, lecturer.getQualifications());
                stmInsert.setString(4, lecturer.getLinkedin());
                stmInsert.executeUpdate();
                ResultSet generatedKeys = stmInsert.getGeneratedKeys();
                generatedKeys.next();
                int lecturerId = generatedKeys.getInt(1);

                String picture = lecturerId + "-" + lecturer.getName();         // path of picture inside the bucket

                if (lecturer.getPicture() != null && !lecturer.getPicture().isEmpty()){
                    PreparedStatement stmUpdateLecturer = connection.prepareStatement("UPDATE lecturer SET picture = ? WHERE id = ?");
                    stmUpdateLecturer.setString(1, picture);
                    stmUpdateLecturer.setInt(2, lecturerId);
                    stmUpdateLecturer.executeUpdate();
                }

                final String table = lecturer.getType().equalsIgnoreCase("full-time")
                        ? "full_time_rank": "part_time_rank";
                Statement stm = connection.createStatement();
                ResultSet rst = stm.executeQuery("SELECT `rank` FROM "+ table +" ORDER BY `rank` DESC LIMIT 1");

                int rank;
                if (!rst.next()) rank =1;
                else {
                    rank = rst.getInt("rank") + 1;
                }

                PreparedStatement stmInsertRank = connection.prepareStatement("INSERT INTO " + table + " (lecturer_id, `rank`) VALUES (?, ?)");
                stmInsertRank.setInt(1, lecturerId);
                stmInsertRank.setInt(2, rank);
                stmInsertRank.executeUpdate();

                String pictureUrl = null;
                if (lecturer.getPicture() != null && !lecturer.getPicture().isEmpty()){
                    Blob blob = bucket.create(picture, lecturer.getPicture().getInputStream(), lecturer.getPicture().getContentType());
                    pictureUrl = blob.signUrl(1, TimeUnit.DAYS, Storage.SignUrlOption.withV4Signature()).toString();
                }

                connection.commit();

                return new LecturerRespTO(lecturerId,
                        lecturer.getName(),
                        lecturer.getDesignation(),
                        lecturer.getQualifications(),
                        lecturer.getType(),
                        pictureUrl,
                        lecturer.getLinkedin());
            } catch (Throwable t) {
                connection.rollback();
                throw t;
            }finally {
                connection.setAutoCommit(true);
            }


        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @PatchMapping("/{lecturer-id}")
    public void updateLecturer(){
        System.out.println("updateLecturer()");
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{lecturer-id}")
    public void deleteLecturer(@PathVariable("lecturer-id") int lecturerId){
        try (Connection connection = pool.getConnection()) {
            PreparedStatement stmExists = connection.prepareStatement("SELECT * FROM lecturer WHERE id = ?");
            stmExists.setInt(1, lecturerId);
            if (!stmExists.executeQuery().next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

            connection.setAutoCommit(false);
            try{
                PreparedStatement stmIdentify = connection.prepareStatement("SELECT l.id, l.name, l.picture, ftr.`rank` ftr, ptr.`rank` ptr FROM lecturer l\n" +
                        "        LEFT OUTER JOIN full_time_rank ftr on l.id = ftr.lecturer_id\n" +
                        "        LEFT OUTER JOIN part_time_rank ptr on l.id = ptr.lecturer_id\n" +
                        "        WHERE l.id = ?");
                stmIdentify.setInt(1, lecturerId);
                ResultSet rst = stmIdentify.executeQuery();
                rst.next();
                int ftr = rst.getInt("ftr");
                int ptr = rst.getInt("ptr");
                String picture = rst.getString("picture");
                String tableName = ftr > 0 ? "full_time_rank" : "part_time_rank";
                int rank = ftr > 0 ? ftr : ptr;

                Statement stmDeleteRank = connection.createStatement();
                stmDeleteRank.executeUpdate("DELETE FROM " + tableName + " WHERE `rank` = " + rank);

                Statement stmShift = connection.createStatement();
                stmShift.executeUpdate("UPDATE " + tableName + " SET `rank` = `rank` -1 WHERE `rank` > " + rank);
                PreparedStatement stmDeleteLecturer = connection.prepareStatement("DELETE FROM lecturer WHERE id = ?");
                stmDeleteLecturer.setInt(1, lecturerId);
                stmDeleteLecturer.executeUpdate();


                if (picture != null) {
                    bucket.get(picture).delete();
                }

                connection.commit();
            } catch (Throwable t) {
                connection.rollback();
                throw t;
            }finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


    }

    @GetMapping
    public void getAllLecturers(){
        System.out.println("getAllLecturers()");
    }
}
