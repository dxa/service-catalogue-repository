
package au.gov.api.servicecatalogue.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

import javax.servlet.http.HttpServletRequest

import khttp.get
import khttp.structures.authorization.BasicAuthorization


@RestController
class APIController {

    @Autowired
    private lateinit var repository: ServiceDescriptionRepository

    @Autowired
    private lateinit var request: HttpServletRequest

    @Autowired
    private lateinit var environment: Environment


    @ResponseStatus(HttpStatus.FORBIDDEN)
    class UnauthorisedToModifyServices() : RuntimeException()
    class UnauthorisedToViewServices() : RuntimeException()

    private fun isAuthorisedToSaveService(request:HttpServletRequest, space:String):Boolean{
        if(environment.getActiveProfiles().contains("prod")){
            val AuthURI = System.getenv("AuthURI")?: throw RuntimeException("No environment variable: AuthURI")

            // http://www.baeldung.com/get-user-in-spring-security
            val raw = request.getHeader("authorization")
            if (raw==null) return false;
            val apikey = String(Base64.getDecoder().decode(raw.removePrefix("Basic ")))
        
            val user = apikey.split(":")[0]
            val pass= apikey.split(":")[1]


            val authorisationRequest = get(AuthURI + "/api/canWrite",
                                            params=mapOf("space" to space),
                                            auth=BasicAuthorization(user, pass)
                                       )
            if(authorisationRequest.statusCode != 200) return false
            return authorisationRequest.text == "true"
        }
        return true
    }


    @CrossOrigin
    @GetMapping("/new")
    fun newService(request:HttpServletRequest, @RequestParam space:String):ServiceDescription{
        val service = ServiceDescription("NewServiceName", "NewServiceDescription", listOf("# Page1"), listOf(), "")

        if(isAuthorisedToSaveService(request, space)) {
            service.metadata.space=space
            repository.save(service)
            return service
        }

        throw UnauthorisedToModifyServices()
    }


    data class IndexDTO(val content:List<IndexServiceDTO>)
    data class IndexServiceDTO(val id:String, val name:String, val description:String, val tags:List<String>, val logoURI:String, val metadata:Metadata)
    @CrossOrigin
    @GetMapping("/index")
    fun index(request:HttpServletRequest): IndexDTO {
        val output = mutableListOf<IndexServiceDTO>()
        val auth = isAuthorisedToSaveService(request,"admin")
        for(service in repository.findAll(auth)){
                output.add(IndexServiceDTO(service.id!!, service.currentContent().name, service.currentContent().description, service.tags, service.logo, service.metadata))
        }
        return IndexDTO(output)
    }


    @CrossOrigin
    data class BackupDTO(val content:Iterable<ServiceDescription>)
    @GetMapping("/backup")
    fun backup(): BackupDTO {
        return BackupDTO(repository.findAll())
    }

    @CrossOrigin
    @GetMapping("/service/{id}")
    fun getService(request:HttpServletRequest, @PathVariable id: String): ServiceDescriptionContent {
        val auth = isAuthorisedToSaveService(request,"admin")
        try{
            val service = repository.findById(id,auth)
            return service.currentContent()
        } catch (e:Exception){
            throw UnauthorisedToViewServices()
        }
    }




    @CrossOrigin
    @PostMapping("/metadata/{id}")
    fun setMetadata(@RequestBody metadata: Metadata, @PathVariable id:String, request:HttpServletRequest): Metadata{

        val auth = isAuthorisedToSaveService(request, "admin")
        val service = repository.findById(id,auth)
        
        if(auth) {
            service.metadata = metadata
            repository.save(service)
            return service.metadata
        }

        throw UnauthorisedToModifyServices()
    }

    @ResponseStatus(HttpStatus.CREATED)  // 201
    @CrossOrigin
    @PostMapping("/service")
    fun setService(@RequestBody revision: ServiceDescriptionContent, request:HttpServletRequest): ServiceDescription {

        val service = ServiceDescription(revision.name, revision.description, revision.pages, listOf(), "")
        
        if(isAuthorisedToSaveService(request, service.metadata.space)) {
            repository.save(service)
            return service
        }

        throw UnauthorisedToModifyServices()
    }

    @CrossOrigin
    @ResponseStatus(HttpStatus.OK)  // 200
    @PostMapping("/service/{id}")
    fun reviseService(@PathVariable id:String, @RequestBody revision: ServiceDescriptionContent, request:HttpServletRequest): ServiceDescriptionContent {
        val service = repository.findById(id)

        if(isAuthorisedToSaveService(request, service.metadata.space)) {

            service.revise(revision.name, revision.description, revision.pages)

            repository.save(service)
            return service.currentContent()
        }

        throw UnauthorisedToModifyServices()
    }




}
