/*
 * Copyright (c) 2019. Kaleido Biosciences. All Rights Reserved
 */

package com.kaleido.cabinetclient.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaleido.cabinetclient.CabinetClientProperties;
import com.kaleido.cabinetclient.authentication.UserCredentials;
import com.kaleido.cabinetclient.domain.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static org.junit.Assert.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(properties = {
        "Cabinet.client.retryInterval=50",
        "Cabinet.client.maxRequestAttempts=3"
})
@RunWith(SpringRunner.class)
public class CabinetClientTest {

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    CabinetClientProperties CabinetClientProperties;

    @Autowired
    CabinetClient<PlateMap> CabinetClient;

    @Autowired
    CabinetClient<PlateMap> plateMapCabinetClient;

    @Autowired
    UserCredentials userCredentials;

    @Autowired
    ObjectMapper objectMapper;

    private MockRestServiceServer server;
    private static final String FAKE_BEARER_TOKEN = "fake.bearer.token";
    private static final Instant FAKE_BEARER_NOT_EXPIRED = Instant.now().plus(Duration.ofDays(1L));


    @Before
    public void setUp() {
        userCredentials.setBearerToken(FAKE_BEARER_TOKEN);
        userCredentials.setBearerExpiry(FAKE_BEARER_NOT_EXPIRED);
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @After
    public void tearDown() {
        server.reset();
    }

    @Test
    public void findById() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.find(1L);
        server.verify();
    }

    @Test
    public void findOneByMethod() {
        String methodName = "/label";
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + methodName + "/test"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findOneByMethod("label", "test");
        server.verify();
    }

    @Test(expected = HttpClientErrorException.class)
    public void findOneByMethodNoEndpointShouldThrowNotFound() {
        String methodName = "/label";
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + methodName + "/test"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        CabinetClient.findOneByMethod("label", "test");
        server.verify();
    }

    @Test
    public void search() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getSearchPathComponent() + "/"
                        + "plate-maps?query=foo&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.search("foo");
        server.verify();
    }

    @Test
    public void searchWithPageing() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getSearchPathComponent() + "/"
                        + "plate-maps?query=foo&page=10&size=30"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.search("foo", 10, 30);
        server.verify();
    }

    @Test
    public void searchRetryOnce() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getSearchPathComponent() + "/"
                        + "plate-maps?query=foo&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getSearchPathComponent() + "/"
                        + "plate-maps?query=foo&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.search("foo");
        server.verify();
    }

    @Test
    public void searchRetryTwice() {
        server.expect(ExpectedCount.twice(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getSearchPathComponent() + "/"
                        + "plate-maps?query=foo&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getSearchPathComponent() + "/"
                        + "plate-maps?query=foo&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.search("foo");
        server.verify();
    }

    @Test(expected = CabinetClientHTTPException.class)
    public void searchShouldNotTryMoreThanThreeTimes() throws CabinetClientHTTPException {
        server.expect(ExpectedCount.times(3),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getSearchPathComponent() + "/"
                        + "plate-maps?query=foo&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        CabinetClient.search("foo");
        server.verify();
    }

    @Test(expected = HttpServerErrorException.class)
    public void searchOtherExceptionShouldNotRetry() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getSearchPathComponent() + "/"
                        + "plate-maps?query=foo&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        CabinetClient.search("foo");
        server.verify();
    }

    @Test
    public void findByFieldEquals() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.equals=0&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findByFieldEquals("id", "0");
        server.verify();
    }

    @Test
    public void findByFieldEqualsRetryOnce() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.equals=0&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.equals=0&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findByFieldEquals("id", "0");
        server.verify();
    }

    @Test
    public void findByFieldEqualsRetryTwice() {
        server.expect(ExpectedCount.twice(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.equals=0&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.equals=0&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findByFieldEquals("id", "0");
        server.verify();
    }

    @Test(expected = CabinetClientHTTPException.class)
    public void findByFieldEqualsShouldNotTryMoreThanThreeTimes() throws CabinetClientHTTPException {
        server.expect(ExpectedCount.times(3),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.equals=0&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        CabinetClient.findByFieldEquals("id", "0");
        server.verify();
    }

    @Test(expected = HttpServerErrorException.class)
    public void findByFieldEqualsOtherExceptionShouldNotRetry() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.equals=0&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        CabinetClient.findByFieldEquals("id", "0");
        server.verify();
    }

    @Test
    public void findByFieldEqualsWithPaging() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.equals=0&page=3&size=10"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findByFieldEquals("id", "0", 3, 10);
        server.verify();
    }

    @Test
    public void findByFieldsEqual() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?name.equals=baa&source.equals=baz&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        Map<String, String> params = new LinkedHashMap<>(); //insertion ordered map
        params.put("name", "baa");
        params.put("source", "baz");

        CabinetClient.findByFieldsEqual(params);
        server.verify();
    }

    @Test
    public void findByFieldsEqualWithPaging() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?name.equals=baa&source.equals=baz&page=10&size=50"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        Map<String, String> params = new LinkedHashMap<>(); //insertion ordered map
        params.put("name", "baa");
        params.put("source", "baz");

        CabinetClient.findByFieldsEqual(params, 10, 50);
        server.verify();
    }

    @Test
    public void findByFieldWithOperator() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findByFieldWithOperator("id", "15", "greaterThan");
        server.verify();
    }

    @Test
    public void findByFieldWithOperatorWithPaging() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&page=3&size=10"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findByFieldWithOperator("id", "15", "greaterThan", 3, 10);
        server.verify();
    }

    @Test
    public void findByFieldsWithOperators() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&name.equals=baz&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        Map<String, String> field1 = Stream.of(new String[][]{{"operator", "greaterThan"}, {"value", "15"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        Map<String, String> field2 = Stream.of(new String[][]{{"operator", "equals"}, {"value", "baz"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        params.put("id", field1);
        params.put("name", field2);

        CabinetClient.findByFieldsWithOperators(params);
        server.verify();
    }

    @Test
    public void findByFieldsWithOperatorsRetryOnce() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&name.equals=baz&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&name.equals=baz&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        Map<String, String> field1 = Stream.of(new String[][]{{"operator", "greaterThan"}, {"value", "15"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        Map<String, String> field2 = Stream.of(new String[][]{{"operator", "equals"}, {"value", "baz"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        params.put("id", field1);
        params.put("name", field2);

        CabinetClient.findByFieldsWithOperators(params);
        server.verify();
    }

    @Test
    public void findByFieldsWithOperatorsRetryTwice() {
        server.expect(ExpectedCount.twice(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&name.equals=baz&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&name.equals=baz&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        Map<String, String> field1 = Stream.of(new String[][]{{"operator", "greaterThan"}, {"value", "15"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        Map<String, String> field2 = Stream.of(new String[][]{{"operator", "equals"}, {"value", "baz"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        params.put("id", field1);
        params.put("name", field2);

        CabinetClient.findByFieldsWithOperators(params);
        server.verify();
    }

    @Test(expected = CabinetClientHTTPException.class)
    public void findByFieldsWithOperatorsShouldNotTryMoreThanThreeTimes() throws CabinetClientHTTPException {
        server.expect(ExpectedCount.times(3),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&name.equals=baz&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        Map<String, String> field1 = Stream.of(new String[][]{{"operator", "greaterThan"}, {"value", "15"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        Map<String, String> field2 = Stream.of(new String[][]{{"operator", "equals"}, {"value", "baz"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        params.put("id", field1);
        params.put("name", field2);

        CabinetClient.findByFieldsWithOperators(params);
        server.verify();
    }

    @Test(expected = HttpServerErrorException.class)
    public void findByFieldsWithOperatorsOtherExceptionShouldNotRetry() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&name.equals=baz&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        Map<String, String> field1 = Stream.of(new String[][]{{"operator", "greaterThan"}, {"value", "15"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        Map<String, String> field2 = Stream.of(new String[][]{{"operator", "equals"}, {"value", "baz"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        params.put("id", field1);
        params.put("name", field2);

        CabinetClient.findByFieldsWithOperators(params);
        server.verify();
    }

    @Test
    public void findByFieldsWithOperatorsWithPaging() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?id.greaterThan=15&name.equals=baz&page=10&size=50"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        Map<String, String> field1 = Stream.of(new String[][]{{"operator", "greaterThan"}, {"value", "15"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        Map<String, String> field2 = Stream.of(new String[][]{{"operator", "equals"}, {"value", "baz"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        params.put("id", field1);
        params.put("name", field2);

        CabinetClient.findByFieldsWithOperators(params, 10, 50);
        server.verify();
    }

    @Test
    public void findAll() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() +
                        "?page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findAll();
        server.verify();
    }

    @Test
    public void findAllWithPaging() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() +
                        "?page=5&size=12"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findAll(5, 12);
        server.verify();
    }

    @Test
    public void findAllRetryOnce() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() +
                        "?page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() +
                        "?page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findAll();
        server.verify();
    }

    @Test
    public void findAllRetryTwice() {
        server.expect(ExpectedCount.twice(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() +
                        "?page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() +
                        "?page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findAll();
        server.verify();
    }

    @Test(expected = CabinetClientHTTPException.class)
    public void findAllShouldNotTryMoreThanThreeTimes() throws CabinetClientHTTPException {
        server.expect(ExpectedCount.times(3),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() +
                        "?page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        CabinetClient.findAll();
        server.verify();
    }

    @Test(expected = HttpServerErrorException.class)
    public void findAllOtherExceptionShouldNotRetry() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() +
                        "?page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        CabinetClient.findAll();
        server.verify();
    }

    @Test
    public void save() throws Exception {
        PlateMap plateMap = new PlateMap().activityName("G123BBB");
        String jsonString = objectMapper.writeValueAsString(plateMap);

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(jsonString))
                .andRespond(withSuccess());

        CabinetClient.save(plateMap);
        server.verify();
    }

    @Test
    public void saveAll() throws Exception {
        List<PlateMap> plateMaps = new ArrayList();
        plateMaps.add(new PlateMap().activityName("G123BBB"));
        plateMaps.add(new PlateMap().activityName("G345CCC"));

        String jsonString = objectMapper.writeValueAsString(plateMaps);
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()
                        + "/save-all"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(jsonString))
                .andRespond(withSuccess());

        CabinetClient.saveAll(plateMaps);
        server.verify();
    }

    @Test(expected = HttpClientErrorException.class)
    public void saveAllNoEndpointShouldThrowNotFoundError() throws Exception {
        List<PlateMap> plateMaps = new ArrayList();
        plateMaps.add(new PlateMap().activityName("G123BBB"));
        plateMaps.add(new PlateMap().activityName("G345CCC"));

        String jsonString = objectMapper.writeValueAsString(plateMaps);
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()
                        + "/save-all"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(jsonString))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        CabinetClient.saveAll(plateMaps);
        server.verify();
    }

    @Test
    public void saveWithSetId() throws Exception {
        PlateMap plateMap = new PlateMap().activityName("G123BBB");
        plateMap.setId(10L);

        String jsonString = objectMapper.writeValueAsString(plateMap);

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json(jsonString))
                .andRespond(withSuccess());

        CabinetClient.save(plateMap);
        server.verify();
    }

    @Test
    public void saveRetryOnce() throws JsonProcessingException {
        PlateMap plateMap = new PlateMap().activityName("G123BBB");
        String jsonString = objectMapper.writeValueAsString(plateMap);

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(jsonString))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(jsonString))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.save(plateMap);
        server.verify();
    }

    @Test
    public void saveRetryTwice() throws JsonProcessingException {
        PlateMap plateMap = new PlateMap().activityName("G123BBB");
        String jsonString = objectMapper.writeValueAsString(plateMap);

        server.expect(ExpectedCount.twice(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(jsonString))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(jsonString))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.save(plateMap);
        server.verify();
    }

    @Test(expected = CabinetClientHTTPException.class)
    public void saveShouldNotTryMoreThanThreeTimes() throws JsonProcessingException, CabinetClientHTTPException {
        PlateMap plateMap = new PlateMap().activityName("G123BBB");
        String jsonString = objectMapper.writeValueAsString(plateMap);

        server.expect(ExpectedCount.times(3),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(jsonString))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        CabinetClient.save(plateMap);
        server.verify();
    }

    @Test(expected = HttpServerErrorException.class)
    public void saveOtherExceptionShouldNotRetry() throws JsonProcessingException {
        PlateMap plateMap = new PlateMap().activityName("G123BBB");
        String jsonString = objectMapper.writeValueAsString(plateMap);

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint()))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(jsonString))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        CabinetClient.save(plateMap);
        server.verify();
    }


    @Test
    public void delete() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "/21"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.delete(21L);
        server.verify();
    }

    @Test
    public void deleteRetryOnce() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "/21"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "/21"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.delete(21L);
        server.verify();
    }

    @Test
    public void deleteRetryTwice() {
        server.expect(ExpectedCount.twice(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "/21"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "/21"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.delete(21L);
        server.verify();
    }

    @Test(expected = CabinetClientHTTPException.class)
    public void deleteShouldNotTryMoreThanThreeTimes() throws CabinetClientHTTPException {
        server.expect(ExpectedCount.times(3),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "/21"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        CabinetClient.delete(21L);
        server.verify();
    }

    @Test(expected = HttpServerErrorException.class)
    public void deleteOtherExceptionShouldNotRetry() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "/21"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        CabinetClient.delete(21L);
        server.verify();
    }

    @Test
    public void findByName() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?name.equals=foo&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findByName("foo");
        server.verify();
    }

    @Test
    public void findByNameWithPaging() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?name.equals=foo&page=1&size=10"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findByName("foo", 1, 10);
        server.verify();
    }

    @Test
    public void findFirstByName() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?name.equals=foo&page=0&size=1"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        CabinetClient.findFirstByName("foo");
        server.verify();
    }


    @Test
    public void findByLabel() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?label.equals=foo&page=0&size=" + MAX_VALUE))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        plateMapCabinetClient.findByLabel("foo");
        server.verify();
    }


    @Test
    public void findByLabelWithPaging() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?label.equals=foo&page=1&size=10"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        plateMapCabinetClient.findByLabel("foo", 1, 10);
        server.verify();
    }

    @Test
    public void findFirstByLabel() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?label.equals=name&page=0&size=1"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        plateMapCabinetClient.findFirstByLabel("name");
        server.verify();
    }


    @Test
    public void findByFieldEqualsUri() {
        String uriString = CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "?foo.equals=baa&page=0&size=" + MAX_VALUE;
        URI uri = CabinetClient.findByFieldEqualsUri("foo", "baa");
        assertEquals(uriString, uri.toString());
    }

    @Test
    public void findByFieldsEqualUri() {
        String uriString = CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "?foo.equals=baa&foz.equals=baz%20%2B%20boz&page=0&size=20";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("foo", "baa");
        params.put("foz", "baz + boz");
        URI uri = CabinetClient.findByFieldsEqualUri(params, 0, 20);
        assertEquals(uriString, uri.toString());
    }

    @Test
    public void findByFieldWithOperatorsUri() {
        String uriString = CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "?foo.greaterThan=15&page=0&size=" + MAX_VALUE;
        URI uri = CabinetClient.findByFieldWithOperatorUri("foo", "15", "greaterThan");
        assertEquals(uriString, uri.toString());
    }

    @Test
    public void findByFieldsWithOperatorsUri() {
        String uriString = CabinetClientProperties.getBase() + CabinetClientProperties.getPlateMapEndpoint() + "?foo.greaterThan=15&bar.equals=baz&page=0&size=20";
        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        Map<String, String> field1 = Stream.of(new String[][]{{"operator", "greaterThan"}, {"value", "15"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        Map<String, String> field2 = Stream.of(new String[][]{{"operator", "equals"}, {"value", "baz"},}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        params.put("foo", field1);
        params.put("bar", field2);
        URI uri = CabinetClient.findByFieldsWithOperatorsUri(params, 0, 20);
        assertEquals(uriString, uri.toString());
    }

    @Test
    public void searchUri() {
        String uriString = CabinetClientProperties.getBase() + CabinetClientProperties.getSearchPathComponent()
                + "/" + CabinetClientProperties.getPlateMapEndpoint() + "?query=foo&page=10&size=15";
        URI uri = CabinetClient.searchUri("foo", 10, 15);

        assertEquals(uriString, uri.toString());
    }

    @Test
    public void getEntityClassName() {
        assertEquals(PlateMap.class.toString(), CabinetClient.getEntityClassName());
    }

    @Test
    public void getEntityClass() {
        assertSame(PlateMap.class, CabinetClient.getEntityClass());
    }

    @Test
    public void childEntityIdsAreValidFieldForSample() {
        server.expect(ExpectedCount.once(),
                requestTo(CabinetClientProperties.getBase()
                        + "plate-maps?wellId.equals=1&communityId.equals=1&plateMapId.equals=1&mediaId.equals=1&page=10&size=50"))
                .andExpect(header("Authorization", "Bearer " + userCredentials.getBearerToken()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        Map<String, String> params = new LinkedHashMap<>(); //insertion ordered map
        params.put("wellId", "1");
        params.put("communityId", "1");
        params.put("plateMapId", "1");
        params.put("mediaId", "1");

        plateMapCabinetClient.findByFieldsEqual(params, 10, 50);
        server.verify();
    }
}
