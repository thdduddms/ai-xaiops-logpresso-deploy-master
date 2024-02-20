package com.exem.xaiops.autodeployer.gitmanagement;

import com.exem.xaiops.autodeployer.deploy.DeployMapper;
import com.exem.xaiops.autodeployer.vo.AdminResponse;
import com.exem.xaiops.autodeployer.vo.LogpressoMeta;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.exem.xaiops.autodeployer.Constant.SOURCE_LP;
import static com.exem.xaiops.autodeployer.Constant.TARGET_LP;

@RestController
public class GitController {
    private final Map<DeployMapper, GitMgmt<?>> git = new HashMap<>();

    public GitController(final List<GitMgmt<?>> gitBeans) {
        gitBeans.forEach(bean -> git.put(bean.getMapper(), bean));
    }

    @ApiOperation(value = "source LP의 데이터를 모두 받아와 txt file 생성", notes = "\tobject_type : procedure(프로시저), schedule(예약쿼리) 유형 사용 가능")
    @PostMapping("/sourceLP/git/all")
    public AdminResponse<?> createAllTextFilesSource(@RequestBody final LogpressoMeta gitMeta) {
        final String objectType = gitMeta.getObject_type().trim();

        // 파일 생성된 개수만 저장
        final int count = git.get(DeployMapper.find(objectType)).createTxtFiles(SOURCE_LP).size();
        return new AdminResponse<>(true, count);
    }

    @ApiOperation(value = "source LP의 데이터를 한 개 받아와 txt file 생성", notes = "\tobject_type : procedure(프로시저), schedule(예약쿼리) 유형 사용 가능")
    @PostMapping("/sourceLP/git/one")
    public AdminResponse<?> createTextFileSource(@RequestBody final LogpressoMeta.Backup gitMeta) {
        final String objectType = gitMeta.getObject_type().trim();
        final String objectName = gitMeta.getObject_name().trim();
        final Object result = git.get(DeployMapper.find(objectType)).generateFileByName(SOURCE_LP, objectName);

        return new AdminResponse<>(result, 1);
    }
    @ApiOperation(value = "target LP의 데이터를 모두 받아와 txt file 생성", notes = "\tobject_type : procedure(프로시저), schedule(예약쿼리) 유형 사용 가능")
    @PostMapping("/targetLP/git/all")
    public AdminResponse<?> createAllTextFilesTarget(@RequestBody final LogpressoMeta gitMeta) {
        final String objectType = gitMeta.getObject_type().trim();

        // 파일 생성된 개수만 저장
        final int count = git.get(DeployMapper.find(objectType)).createTxtFiles(TARGET_LP).size();
        return new AdminResponse<>(true, count);
    }

    @ApiOperation(value = "target LP의 데이터를 한 개 받아와 txt file 생성", notes = "\tobject_type : procedure(프로시저), schedule(예약쿼리) 유형 사용 가능")
    @PostMapping("/targetLP/git/one")
    public AdminResponse<?> createTextFileTarget(@RequestBody final LogpressoMeta.Backup gitMeta) {
        final String objectType = gitMeta.getObject_type().trim();
        final String objectName = gitMeta.getObject_name().trim();
        final Object result = git.get(DeployMapper.find(objectType)).generateFileByName(TARGET_LP, objectName);

        return new AdminResponse<>(result, 1);
    }
}
