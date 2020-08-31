package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.apache.commons.lang.time.DateUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static final DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;
    private static ParkingService parkingService;
    private static Connection connection;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    private static void setUp() {
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
    }

    @AfterAll
    private static void tearDown() {

    }


    @BeforeEach
    private void setUpPerTest() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("CAR1");
        dataBasePrepareService.clearDataBaseEntries();
        connection = dataBaseTestConfig.getConnection();
        parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

    }


//        "insert into ticket(PARKING_NUMBER, VEHICLE_REG_NUMBER, PRICE, IN_TIME, OUT_TIME) values(?,?,?,?,?)";

    @Test
    public void testParkingACar() throws SQLException, ClassNotFoundException {


        // GIVEN
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        Connection connection = dataBaseTestConfig.getConnection();

        // WHEN
        parkingService.processIncomingVehicle();

        // THEN
        String preparedString = "SELECT t.*, p.AVAILABLE, p.TYPE FROM ticket t, parking p WHERE t.PARKING_NUMBER = p.PARKING_NUMBER;";
        PreparedStatement prepStatement = connection.prepareStatement(preparedString);
        ResultSet rs = prepStatement.executeQuery();

        while (rs.next()) {

            String resultat = rs.getString("AVAILABLE");
            String resultat2 = rs.getString("VEHICLE_REG_NUMBER");
            assertEquals("0", resultat);
            assertEquals("CAR1", resultat2);
        }
        dataBaseTestConfig.closePreparedStatement(prepStatement);
        dataBaseTestConfig.closeResultSet(rs);
        dataBaseTestConfig.closeConnection(connection);

    }


    @Test
    public void testParkingLotExit() throws SQLException {



        // GIVEN


        parkingService.processIncomingVehicle();
//        changing IN_TIME in DB

        String subOneHourString =  "UPDATE ticket SET IN_TIME = SUBTIME (IN_TIME, '01:00:00') WHERE ID = 1;";
        PreparedStatement subOneHour = connection.prepareStatement(subOneHourString);

        subOneHour.execute();
        // WHEN
        parkingService.processExitingVehicle();

        // THEN
        String preparedString = "SELECT t.*, p.AVAILABLE, p.TYPE FROM ticket t, parking p WHERE t.PARKING_NUMBER = p.PARKING_NUMBER;";
        PreparedStatement prepStatement = connection.prepareStatement(preparedString);
        ResultSet rs = prepStatement.executeQuery();

        while (rs.next()) {
            Timestamp outTime = rs.getTimestamp("OUT_TIME");
            String availability = rs.getString("AVAILABLE");
            Double price = rs.getDouble("PRICE");
//            check if parkinglot is freed
            assertEquals("1", availability);
//            check if out_time is populated correctly in DB
            assertEquals(DateUtils.round(new Timestamp(System.currentTimeMillis()), Calendar.SECOND), DateUtils.round(outTime, Calendar.SECOND));//Testing if DB OUT_TIME is within 1sec
//          check if fare generated is populated correctly in DB
            assertEquals(Fare.CAR_RATE_PER_HOUR, price, 0.001);
        }

        dataBaseTestConfig.closePreparedStatement(prepStatement);
        dataBaseTestConfig.closeResultSet(rs);
        dataBaseTestConfig.closeConnection(connection);
    }

}
